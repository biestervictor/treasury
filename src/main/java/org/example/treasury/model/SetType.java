package org.example.treasury.model;

/**
 * Enum representing different display types for Magic: The Gathering sets.
 */
public enum SetType {
  DRAFT("draft"),
  MASTERS("masters"),
  FUNNY("funny"),
  EXPANSION("expansion"),
  CORE("core"),
  DRAFT_INNOVATION("draft_innovation");

  private final String value;

  SetType(String value) {
    this.value = value;
  }

  public String getKey() {
    return name();
  }

  public String getValue() {
    return value;
  }

  public static boolean containsValue(String value) {
    for (SetType type : SetType.values()) {
      if (type.getValue().equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }
}
