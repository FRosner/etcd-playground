package de.frosner.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NodeTest {

  @RegisterExtension
  public static final EtcdClusterExtension etcdCluster = new EtcdClusterExtension(
      NodeTest.class.getSimpleName(), 1);

  @Test
  public void testNodeDataInitialLoad() throws Exception {
    try (Node node1 = new Node(etcdCluster.getClientEndpoints())) {
      node1.join();
      try (Node node2 = new Node(etcdCluster.getClientEndpoints())) {
        node2.join();
        assertThat(node2.getClusterMembers()).contains(node1.getNodeData());
      }
      // TODO wait for watcher with awaitility
      Thread.sleep(1000);
    }
  }
}