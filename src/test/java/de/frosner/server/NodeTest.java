package de.frosner.server;

import io.etcd.jetcd.test.EtcdClusterExtension;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NodeTest {

  @RegisterExtension
  public static final EtcdClusterExtension etcdCluster = new EtcdClusterExtension(
      NodeTest.class.getSimpleName(), 1);

  @Test
  public void testTwoNodesJoinLeave() throws Exception {
    try (Node node1 = new Node(etcdCluster.getClientEndpoints())) {
      node1.join();
      try (Node node2 = new Node(etcdCluster.getClientEndpoints())) {
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