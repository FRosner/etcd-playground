package de.frosner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void run()
    {
        logger.info("Bla");
    }
}
