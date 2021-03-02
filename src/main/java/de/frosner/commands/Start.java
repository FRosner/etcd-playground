package de.frosner.commands;

import de.frosner.server.JoinFailedException;
import de.frosner.server.LeaveFailedException;
import de.frosner.server.Node;
import java.net.URI;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(
    name = "start",
    mixinStandardHelpOptions = true,
    description = "Starts a new node."
)
public class Start implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(Start.class);

  public static void main(String[] args) {
    int exitCode = new CommandLine(new Start()).execute(args);
    System.exit(exitCode);
  }

  @CommandLine.Option(
      names = {"-e", "--endpoints"},
      split = ",",
      defaultValue = "http://localhost:2379"
  )
  URI[] endpoints;

  @Override
  public void run() {
    try (Node node = new Node(Arrays.asList(endpoints))) {
      node.join();
    } catch (JoinFailedException e) {
      logger.error("Failed to start node.", e);
    } catch (LeaveFailedException e) {
      logger.error("Failed to clean up.", e);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
