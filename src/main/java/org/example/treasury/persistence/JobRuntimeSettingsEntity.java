package org.example.treasury.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "job_runtime_settings")
public class JobRuntimeSettingsEntity {

  @Id
  private String id;

  /** JobKey name, e.g. SELL */
  private String key;

  private boolean enabled;

  /** Cron string in Spring format with seconds. */
  private String cron;

  /** Optional ZoneId string, null means system default. */
  private String zoneId;

  private Instant updatedAt;

  public JobRuntimeSettingsEntity() {
  }

  public JobRuntimeSettingsEntity(String key, boolean enabled, String cron, String zoneId, Instant updatedAt) {
    this.key = key;
    this.enabled = enabled;
    this.cron = cron;
    this.zoneId = zoneId;
    this.updatedAt = updatedAt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getZoneId() {
    return zoneId;
  }

  public void setZoneId(String zoneId) {
    this.zoneId = zoneId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}

