package de.frosner.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.etcd.jetcd.test.EtcdClusterExtension;

class NodeTest
{

    @RegisterExtension
    public static final EtcdClusterExtension etcdCluster = new EtcdClusterExtension(NodeTest.class.getSimpleName(), 1);

    @Test
    public void test()
    {
        try (Node node = new Node(etcdCluster.getClientEndpoints()))
        {
            node.join();
        }
    }
}