package de.frosner.server;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.frosner.commands.Start;
import io.etcd.jetcd.Client;

public class Node implements AutoCloseable
{
    private static final Logger logger = LoggerFactory.getLogger(Start.class);

    private final Client etcdClient;

    public Node(List<URI> endpoints)
    {
        logger.info("Connecting to etcd on the following endpoints: {}", endpoints);
        etcdClient = Client.builder().endpoints(endpoints).build();
    }

    public void join()
    {
        logger.info("Joining the cluster");
        // TODO join cluster
    }

    public void leave()
    {
        logger.info("Leaving the cluster");
        // TODO leave cluster
    }

    @Override
    public void close()
    {
        leave();
        etcdClient.close();
    }
}
