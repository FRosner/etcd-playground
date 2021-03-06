package de.frosner.server;

import de.frosner.util.JsonObjectMapper;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.shaded.com.google.common.collect.ImmutableSet;
import io.etcd.jetcd.shaded.io.grpc.stub.StreamObserver;
import io.etcd.jetcd.support.CloseableClient;
import io.etcd.jetcd.watch.WatchEvent;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node implements AutoCloseable {

  private final Logger logger;

  private static final int OPERATION_TIMEOUT = 5;
  private static final long DEFAULT_LEASE_TTL = 5;
  public static final String NODES_PREFIX = "/nodes/";

  private final NodeData nodeData;

  private final Client etcdClient;
  private Watch.Watcher watcher;
  private final long leaseTtl;
  private volatile long leaseId;
  private volatile CloseableClient keepAliveClient;

  private final ConcurrentHashMap<UUID, NodeData> clusterMembers = new ConcurrentHashMap<>();

  public Node(List<URI> endpoints) throws Exception {
    this(endpoints, DEFAULT_LEASE_TTL);
  }

  public Node(List<URI> endpoints, long leaseTtl) throws Exception {
    this.leaseTtl = leaseTtl;
    nodeData = new NodeData(UUID.randomUUID());
    logger = LoggerFactory.getLogger(nodeData.getUuid().toString());
    logger.info("Connecting to etcd on the following endpoints: {}", endpoints);
    etcdClient = Client.builder().endpoints(endpoints).build();
    long maxModRevision = loadMembershipSnapshot();
    watchMembershipChanges(maxModRevision);
  }

  private long loadMembershipSnapshot() throws Exception {
    // TODO handle pagination?
    GetResponse response = etcdClient.getKVClient().get(
        ByteSequence.from(NODES_PREFIX, StandardCharsets.UTF_8),
        GetOption.newBuilder()
            .withPrefix(ByteSequence.from(NODES_PREFIX, StandardCharsets.UTF_8))
            .build()
    ).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    for (KeyValue kv : response.getKvs()) {
      NodeData nodeData = JsonObjectMapper.INSTANCE
          .readValue(kv.getValue().toString(StandardCharsets.UTF_8), NodeData.class);
      logger.info("LOAD {}", nodeData);
      clusterMembers.put(nodeData.getUuid(), nodeData);
    }
    return response.getKvs().stream()
        .mapToLong(KeyValue::getModRevision).max().orElse(0);
  }

  private void watchMembershipChanges(long fromRevision) {
    logger.info("Watching membership changes from revision {}", fromRevision);
    watcher = etcdClient.getWatchClient().watch(
        ByteSequence.from(NODES_PREFIX, StandardCharsets.UTF_8),
        WatchOption.newBuilder()
            .withPrefix(ByteSequence.from(NODES_PREFIX, StandardCharsets.UTF_8))
            .withRevision(fromRevision) // TODO watch from revision + 1
            .build(),
        watchResponse -> {
          // TODO handle watch response on separate executor to not block grpc-default-executor
          watchResponse.getEvents().forEach(this::handleWatchEvent);
        },
        error -> logger.error("Watcher broke", error),
        () -> logger.info("Watcher completed")
    );
  }

  private void handleWatchEvent(WatchEvent watchEvent) {
    try {
      switch (watchEvent.getEventType()) {
        case PUT:
          NodeData nodeData = JsonObjectMapper.INSTANCE
              .readValue(watchEvent.getKeyValue().getValue().toString(StandardCharsets.UTF_8),
                  NodeData.class);
          logger.info("PUT {}", nodeData);
          clusterMembers.put(nodeData.getUuid(), nodeData);
          break;
        case DELETE:
          String etcdKey = watchEvent.getKeyValue().getKey().toString(StandardCharsets.UTF_8);
          UUID nodeUuid = UUID.fromString(extractNodeUuid(etcdKey));
          logger.info("DELETE {}", nodeUuid);
          clusterMembers.remove(nodeUuid);
          break;
        default:
          logger.warn("Unrecognized event: {}", watchEvent.getEventType());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to handle watch event", e);
    }
  }

  private String extractNodeUuid(String etcdKey) {
    return etcdKey.replaceAll(Pattern.quote(NODES_PREFIX), "");
  }

  public void join() throws JoinFailedException {
    try {
      logger.info("Joining the cluster");
      grantLease();
      putMetadata();
      logger.info("Join complete");
    } catch (Exception e) {
      throw new JoinFailedException(nodeData, e);
    }
  }

  private void grantLease() throws Exception {
    Lease leaseClient = etcdClient.getLeaseClient();
    logger.info("Granting lease");
    leaseClient.grant(leaseTtl)
        .thenAccept((leaseGrantResponse -> {
          leaseId = leaseGrantResponse.getID();
          logger.info("Lease {} granted", leaseId);
          keepAliveClient = leaseClient.keepAlive(leaseId,
              new StreamObserver<>() {
                @Override
                public void onNext(LeaseKeepAliveResponse leaseKeepAliveResponse) {
                  logger.debug("Kept lease {} alive", leaseId);
                }

                @Override
                public void onError(Throwable throwable) {
                  logger.error("Failed to keep lease {} alive", leaseId);
                }

                @Override
                public void onCompleted() {
                  logger.debug("Lease completed");
                }
              });
        })).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
  }

  private void putMetadata() throws Exception {
    logger.info("Putting node metadata");
    etcdClient.getKVClient().put(
        ByteSequence.from(
            NODES_PREFIX + nodeData.getUuid(),
            StandardCharsets.UTF_8
        ),
        ByteSequence.from(
            JsonObjectMapper.INSTANCE.writeValueAsString(nodeData),
            StandardCharsets.UTF_8
        ),
        PutOption.newBuilder().withLeaseId(leaseId).build()
    ).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
  }

  public void leave() throws LeaveFailedException {
    try {
      logger.info("Leaving the cluster");
      if (keepAliveClient != null) {
        keepAliveClient.close();
      }
      etcdClient.getLeaseClient().revoke(leaseId)
          .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new LeaveFailedException(nodeData, e);
    }
  }

  public Set<NodeData> getClusterMembers() {
    return ImmutableSet.copyOf(clusterMembers.values());
  }

  public NodeData getNodeData() {
    return nodeData;
  }

  @Override
  public void close() throws LeaveFailedException {
    leave();
    watcher.close();
    etcdClient.close();
  }
}
