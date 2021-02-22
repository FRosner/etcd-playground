package de.frosner.server;

import java.util.UUID;

public class LeaveFailedException extends Exception {

  public LeaveFailedException(UUID nodeId, Exception cause) {
    super(String.format("Node %s failed to leave.", nodeId, cause));
  }
}
