package org.example.treasury.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.example.treasury.config.JobSettingsProperties;
import org.example.treasury.model.JobKey;
import org.springframework.stereotype.Service;

/**
 * Einfache In-Memory Runtime-Konfiguration fr Job-Flags.
 *
 * <p>Persistenz ist absichtlich nicht implementiert (Restart setzt Defaults).
 */
@Service
public class JobSettingsService {

  private final AtomicReference<JobSettingsProperties> settings;
  private final AtomicReference<Instant> updatedAt = new AtomicReference<>(Instant.now());
  private final JobRuntimeSettingsService jobRuntimeSettingsService;

  public JobSettingsService(JobSettingsProperties defaults,
                            JobRuntimeSettingsService jobRuntimeSettingsService) {
    this.jobRuntimeSettingsService = jobRuntimeSettingsService;
    // Defensive copy
    JobSettingsProperties copy = new JobSettingsProperties();

    // Initial: nutze Defaults aus application*.properties, aber schreibe sie einmalig in die per-Job Settings,
    // damit UI Änderungen danach stabil sind.
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.SELL, defaults.isSellEnabled(), "0 0 0 * * *", null, Instant.now()));
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.PRICE_SCRAPER, defaults.isPriceScraperEnabled(), "0 0 0 * * *", null, Instant.now()));
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.MAGIC_SET, defaults.isMagicSetEnabled(), "0 0 0 * * *", null, Instant.now()));
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.METAL_PRICE_SCRAPER, defaults.isMetalPriceScraperEnabled(), "0 0 */6 * * *", null, Instant.now()));

    copy.setSellEnabled(jobRuntimeSettingsService.get(JobKey.SELL).enabled());
    copy.setPriceScraperEnabled(jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER).enabled());
    copy.setMagicSetEnabled(jobRuntimeSettingsService.get(JobKey.MAGIC_SET).enabled());
    copy.setMetalPriceScraperEnabled(jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER).enabled());
    this.settings = new AtomicReference<>(copy);
  }

  public JobSettingsProperties get() {
    // immer aus per-Job Settings ableiten
    JobSettingsProperties copy = new JobSettingsProperties();
    copy.setSellEnabled(jobRuntimeSettingsService.get(JobKey.SELL).enabled());
    copy.setPriceScraperEnabled(jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER).enabled());
    copy.setMagicSetEnabled(jobRuntimeSettingsService.get(JobKey.MAGIC_SET).enabled());
    copy.setMetalPriceScraperEnabled(jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER).enabled());
    return copy;
  }

  public Instant getUpdatedAt() {
    return updatedAt.get();
  }

  public void update(boolean sellEnabled, boolean priceScraperEnabled, boolean magicSetEnabled,
                     boolean metalPriceScraperEnabled) {
    Instant now = Instant.now();
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.SELL, sellEnabled, jobRuntimeSettingsService.get(JobKey.SELL).cron(),
        jobRuntimeSettingsService.get(JobKey.SELL).zoneId(), now));
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.PRICE_SCRAPER, priceScraperEnabled, jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER).cron(),
        jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER).zoneId(), now));
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.MAGIC_SET, magicSetEnabled, jobRuntimeSettingsService.get(JobKey.MAGIC_SET).cron(),
        jobRuntimeSettingsService.get(JobKey.MAGIC_SET).zoneId(), now));
    jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
        JobKey.METAL_PRICE_SCRAPER, metalPriceScraperEnabled,
        jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER).cron(),
        jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER).zoneId(), now));

    // legacy ref update
    settings.set(get());
    updatedAt.set(Instant.now());
  }
}
