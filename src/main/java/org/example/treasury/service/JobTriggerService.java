package org.example.treasury.service;

import org.example.treasury.model.JobKey;
import org.example.treasury.job.MagicSetJob;
import org.example.treasury.job.MetalPriceScraperJob;
import org.example.treasury.job.PriceScraperJob;
import org.example.treasury.job.SellJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Kapselt das "Triggern" der Jobs. Damit kann JobSettingsService beim Aktivieren
 * eines Jobs diesen sofort starten, ohne Logging/Threading überall zu duplizieren.
 */
@Service
public class JobTriggerService {

  private static final Logger log = LoggerFactory.getLogger(JobTriggerService.class);

  private final SellJob sellJob;
  private final PriceScraperJob priceScraperJob;
  private final MagicSetJob magicSetJob;
  private final MetalPriceScraperJob metalPriceScraperJob;

  public JobTriggerService(SellJob sellJob,
                           PriceScraperJob priceScraperJob,
                           MagicSetJob magicSetJob,
                           MetalPriceScraperJob metalPriceScraperJob) {
    this.sellJob = sellJob;
    this.priceScraperJob = priceScraperJob;
    this.magicSetJob = magicSetJob;
    this.metalPriceScraperJob = metalPriceScraperJob;
  }

  @Async
  public void trigger(JobKey key) {
    try {
      log.info("Trigger job now: {}", key);
      switch (key) {
        case SELL -> sellJob.triggerNow();
        case PRICE_SCRAPER -> priceScraperJob.triggerNow();
        case MAGIC_SET -> magicSetJob.triggerNow();
        case METAL_PRICE_SCRAPER -> metalPriceScraperJob.triggerNow();
      }
    } catch (Exception e) {
      log.warn("Trigger job {} failed: {}", key, e.getMessage());
    }
  }
}

