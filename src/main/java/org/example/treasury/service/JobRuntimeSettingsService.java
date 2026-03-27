package org.example.treasury.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.example.treasury.model.JobKey;
import org.example.treasury.model.JobRuntimeSettings;
import org.example.treasury.persistence.JobRuntimeSettingsEntity;
import org.example.treasury.repository.JobRuntimeSettingsRepository;
import org.springframework.stereotype.Service;

/**
 * In-Memory Verwaltung von Job-Settings pro Job (enabled + cron).
 *
 * <p>So geschnitten, dass später Persistenz (Mongo/JPA) unkompliziert ergänzt werden kann.</p>
 */
@Service
public class JobRuntimeSettingsService {

  private final AtomicReference<Map<JobKey, JobRuntimeSettings>> ref;
  private final JobRuntimeSettingsRepository repo;

  public JobRuntimeSettingsService(JobRuntimeSettingsRepository repo) {
    this.repo = repo;
    // Defaults entsprechen bisheriger Konfiguration
    Map<JobKey, JobRuntimeSettings> defaults = new EnumMap<>(JobKey.class);
    Instant now = Instant.now();
    defaults.put(JobKey.SELL, new JobRuntimeSettings(JobKey.SELL, false, "0 0 0 * * *", null, now));
    defaults.put(JobKey.PRICE_SCRAPER, new JobRuntimeSettings(JobKey.PRICE_SCRAPER, false, "0 0 0 * * *", null, now));
    defaults.put(JobKey.MAGIC_SET, new JobRuntimeSettings(JobKey.MAGIC_SET, false, "0 0 0 * * *", null, now));
    defaults.put(JobKey.METAL_PRICE_SCRAPER, new JobRuntimeSettings(JobKey.METAL_PRICE_SCRAPER, false, "0 0 */6 * * *", null, now));

    // Wenn in Mongo vorhanden, überschreibe Defaults
    for (JobKey key : JobKey.values()) {
      repo.findByKey(key.name()).ifPresent(e -> {
        defaults.put(key, toModel(e));
      });
    }

    this.ref = new AtomicReference<>(defaults);
  }

  public Map<JobKey, JobRuntimeSettings> getAll() {
    return ref.get();
  }

  public JobRuntimeSettings get(JobKey key) {
    return ref.get().get(key);
  }

  public void upsert(JobRuntimeSettings s) {
    Map<JobKey, JobRuntimeSettings> copy = new EnumMap<>(ref.get());
    copy.put(s.key(), s);
    ref.set(copy);

    JobRuntimeSettingsEntity entity = new JobRuntimeSettingsEntity(
        s.key().name(), s.enabled(), s.cron(), s.zoneId(), s.updatedAt());
    // Upsert by key: reuse existing id if present
    repo.findByKey(s.key().name()).ifPresent(existing -> entity.setId(existing.getId()));
    repo.save(entity);
  }

  private static JobRuntimeSettings toModel(JobRuntimeSettingsEntity e) {
    return new JobRuntimeSettings(JobKey.valueOf(e.getKey()), e.isEnabled(), e.getCron(), e.getZoneId(),
        e.getUpdatedAt() != null ? e.getUpdatedAt() : Instant.now());
  }
}

