package org.example.treasury.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.example.treasury.config.JobSettingsProperties;
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

  public JobSettingsService(JobSettingsProperties defaults) {
    // Defensive copy
    JobSettingsProperties copy = new JobSettingsProperties();
    copy.setSellEnabled(defaults.isSellEnabled());
    copy.setPriceScraperEnabled(defaults.isPriceScraperEnabled());
    copy.setMagicSetEnabled(defaults.isMagicSetEnabled());
    this.settings = new AtomicReference<>(copy);
  }

  public JobSettingsProperties get() {
    return settings.get();
  }

  public Instant getUpdatedAt() {
    return updatedAt.get();
  }

  public void update(boolean sellEnabled, boolean priceScraperEnabled, boolean magicSetEnabled) {
    JobSettingsProperties copy = new JobSettingsProperties();
    copy.setSellEnabled(sellEnabled);
    copy.setPriceScraperEnabled(priceScraperEnabled);
    copy.setMagicSetEnabled(magicSetEnabled);
    settings.set(copy);
    updatedAt.set(Instant.now());
  }
}
