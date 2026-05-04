package org.example.treasury.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB-Dokument für applikationsweite Konfigurationseinträge (key/value). */
@Document(collection = "app_config")
public class AppConfigEntry {

  @Id
  private String id;

  /** Konfigurationsschlüssel, z.B. {@code numista.api-key}. */
  private String key;

  /** Konfigurationswert. */
  private String value;

  /** Zeitpunkt der letzten Änderung. */
  private Instant updatedAt;

  /** No-arg constructor for Spring Data. */
  public AppConfigEntry() {
  }

  /**
   * Erstellt einen neuen Konfigurationseintrag.
   *
   * @param key       Konfigurationsschlüssel
   * @param value     Konfigurationswert
   * @param updatedAt Zeitpunkt der letzten Änderung
   */
  public AppConfigEntry(String key, String value, Instant updatedAt) {
    this.key = key;
    this.value = value;
    this.updatedAt = updatedAt;
  }

  /** Returns the database id. */
  public String getId() {
    return id;
  }

  /** Sets the database id. */
  public void setId(String id) {
    this.id = id;
  }

  /** Returns the config key. */
  public String getKey() {
    return key;
  }

  /** Sets the config key. */
  public void setKey(String key) {
    this.key = key;
  }

  /** Returns the config value. */
  public String getValue() {
    return value;
  }

  /** Sets the config value. */
  public void setValue(String value) {
    this.value = value;
  }

  /** Returns the last update timestamp. */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Sets the last update timestamp. */
  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
