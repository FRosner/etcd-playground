package de.frosner.server;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.WatchOption;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(Node.class);

  private static final int OPERATION_TIMEOUT = 5;
  public static final String NODES_PREFIX = "/nodes/";

  private final UUID nodeId = UUID.randomUUID();

  private final Client etcdClient;
  private final Watch.Watcher watcher;

  public Node(List<URI> endpoints) {
    logger.info("Connecting to etcd on the following endpoints: {}", endpoints);
    etcdClient = Client.builder().endpoints(endpoints).build();
    watcher = etcdClient.getWatchClient().watch(
        ByteSequence.from(NODES_PREFIX, StandardCharsets.UTF_8),
        WatchOption.newBuilder().withPrefix(ByteSequence.from("/nodes", StandardCharsets.UTF_8))
            .build(),
        watchResponse -> {
          // TODO handle watch response on separate executor to not block grpc-default-executor
          logger.info(
              "New node event: {}",
              watchResponse.getEvents()
                  .stream()
                  .map(e -> String.format("%s %s", e.getEventType(),
                      e.getKeyValue().getKey().toString(StandardCharsets.UTF_8)))
                  .collect(Collectors.joining(", "))
          );
        });
  }

  public void join() throws JoinFailedException {
    try {
      logger.info("Joining the cluster");
      // TODO lease
      etcdClient.getKVClient().put(
          ByteSequence.from(NODES_PREFIX + nodeId, StandardCharsets.UTF_8),
          ByteSequence.from(String.format("{\"id\":\"%s\"}", nodeId), StandardCharsets.UTF_8)
      ).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new JoinFailedException(nodeId, e);
    }
  }

  public void leave() throws LeaveFailedException {
    try {
      logger.info("Leaving the cluster");
      // TODO lease
      etcdClient.getKVClient()
          .delete(ByteSequence.from(NODES_PREFIX + nodeId, StandardCharsets.UTF_8))
          .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new LeaveFailedException(nodeId, e);
    }
  }

  @Override
  public void close() throws LeaveFailedException {
    leave();
    watcher.close();
    etcdClient.close();
  }
}
