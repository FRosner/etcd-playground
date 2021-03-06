package de.frosner.server;

import io.etcd.jetcd.launcher.EtcdContainer;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NodeTest {

  @Container
  private static final GenericContainer<?> etcd =
      new GenericContainer<>(EtcdContainer.ETCD_DOCKER_IMAGE_NAME)
          .withCommand("etcd",
              "-listen-client-urls", "http://0.0.0.0:2379",
              "--advertise-client-urls", "http://0.0.0.0:2379",
              "--name", NodeTest.class.getSimpleName())
          .withExposedPorts(2379);

  private List<URI> getClientEndpoints() {
    return List.of(URI.create(
        "https://" + etcd.getContainerIpAddress() + ":" + etcd.getMappedPort(2379)));
  }

  @Test
  public void testTwoNodesJoinLeave() throws Exception {
    try (Node node1 = new Node(getClientEndpoints())) {
      node1.join();
      try (Node node2 = new Node(getClientEndpoints())) {
        node2.join();

        Awaitility.await("Node 1 to see all nodes")
            .until(() -> node1.getClusterMembers()
                .containsAll(List.of(node1.getNodeData(), node2.getNodeData())));
        Awaitility.await("Node 2 to see all nodes")
            .until(() -> node2.getClusterMembers()
                .containsAll(List.of(node1.getNodeData(), node2.getNodeData())));
      }
      Awaitility.await("Node 1 to see that node 2 is gone")
          .until(() -> node1.getClusterMembers()
              .equals(Set.of(node1.getNodeData())));
    }
  }

}