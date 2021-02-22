package de.frosner.server;

import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NodeTest {

  @RegisterExtension
  public static final EtcdClusterExtension etcdCluster = new EtcdClusterExtension(
      NodeTest.class.getSimpleName(), 1);

  @Test
  public void test() throws Exception {
    try (Node node = new Node(etcdCluster.getClientEndpoints())) {
      node.join();
      // TODO wait for watcher with awaitility
      Thread.sleep(1000);
    }
  }
}