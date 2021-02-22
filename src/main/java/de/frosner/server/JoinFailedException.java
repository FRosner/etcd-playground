package de.frosner.server;

import java.util.UUID;

public class JoinFailedException extends Exception {

  public JoinFailedException(UUID nodeId, Exception cause) {
    super(String.format("Node %s failed to join.", nodeId, cause));
  }
}
