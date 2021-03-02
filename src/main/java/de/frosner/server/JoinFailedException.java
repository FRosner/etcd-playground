package de.frosner.server;

public class JoinFailedException extends Exception {

  public JoinFailedException(NodeData nodeData, Exception cause) {
    super(String.format("Node %s failed to join.", nodeData.getUuid(), cause));
  }
}
