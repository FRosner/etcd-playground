package de.frosner.commands;

import java.net.URI;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.frosner.server.Node;
import picocli.CommandLine;

@CommandLine.Command(
        name = "start",
        mixinStandardHelpOptions = true,
        description = "Starts a new node."
)
public class Start implements Runnable
{
    private static final Logger logger = LoggerFactory.getLogger(Start.class);

    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Start()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Option(
            names = { "-e", "--endpoints" },
            split = ",",
            defaultValue = "http://localhost:2379"
    )
    URI[] endpoints;

    @Override
    public void run()
    {
        try (Node node = new Node(Arrays.asList(endpoints)))
        {
            node.join();
        }
    }
}
