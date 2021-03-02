package de.frosner.server;

import de.frosner.util.JsonObjectMapper;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(Node.class);

  private static final int OPERATION_TIMEOUT = 5;
  public static final String NODES_PREFIX = "/nodes/";

  private final NodeData nodeData;

  private final Client etcdClient;
  private Watch.Watcher watcher;

  private final ConcurrentLinkedQueue<NodeData> clusterMembers = new ConcurrentLinkedQueue<>();

  public Node(List<URI> endpoints) throws Exception {
    nodeData = new NodeData(UUID.randomUUID());
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
      clusterMembers.add(nodeData);
    }
    return response.getKvs().stream()
        .mapToLong(KeyValue::getModRevision).max().orElse(0);
  }

  private void watchMembershipChanges(long fromRevision) {
    watcher = etcdClient.getWatchClient().watch(
        ByteSequence.from(NODES_PREFIX, StandardCharsets.UTF_8),
        WatchOption.newBuilder()
            .withPrefix(ByteSequence.from("/nodes", StandardCharsets.UTF_8))
            .withRevision(fromRevision)
            .build(),
        watchResponse -> {
          logger.info(
              "New node events: {}",
              watchResponse.getEvents()
                  .stream()
                  .map(e -> String.format("%s %s", e.getEventType(),
                      e.getKeyValue().getKey().toString(StandardCharsets.UTF_8)))
                  .collect(Collectors.joining(", "))
          );
          // TODO handle watch response on separate executor to not block grpc-default-executor
        });
  }

  public void join() throws JoinFailedException {
    try {
      logger.info("Joining the cluster");
      // TODO lease
      etcdClient.getKVClient().put(
          ByteSequence.from(
              NODES_PREFIX + nodeData.getUuid(),
              StandardCharsets.UTF_8
          ),
          ByteSequence.from(
              JsonObjectMapper.INSTANCE.writeValueAsString(nodeData),
              StandardCharsets.UTF_8
          )
      ).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new JoinFailedException(nodeData, e);
    }
  }

  public void leave() throws LeaveFailedException {
    try {
      logger.info("Leaving the cluster");
      // TODO lease
      etcdClient.getKVClient()
          .delete(ByteSequence.from(NODES_PREFIX + nodeData.getUuid(), StandardCharsets.UTF_8))
          .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new LeaveFailedException(nodeData, e);
    }
  }

  public List<NodeData> getClusterMembers() {
    return new ArrayList<>(clusterMembers);
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
