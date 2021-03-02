package de.frosner.server;

import java.util.Objects;
import java.util.UUID;

public class NodeData {

  private UUID uuid;

  public NodeData() {
  }

  public NodeData(UUID uuid) {
    this.uuid = uuid;
  }

  public UUID getUuid() {
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodeData nodeData = (NodeData) o;
    return Objects.equals(uuid, nodeData.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid);
  }

  @Override
  public String toString() {
    return "NodeData{" +
        "uuid=" + uuid +
        '}';
  }
}
