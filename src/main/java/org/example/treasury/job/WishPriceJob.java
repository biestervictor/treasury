package org.example.treasury.job;

import org.example.treasury.service.JobSettingsService;
import org.example.treasury.service.WishPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly job that fetches booster box prices from MTGStocks for all sets,
 * saves price snapshots, and sends email alerts when wish prices are reached.
 * Default schedule: every Monday at 08:00.
 */
@Component
public class WishPriceJob {

  private static final Logger logger = LoggerFactory.getLogger(WishPriceJob.class);

  private final WishPriceService wishPriceService;
  private final JobSettingsService jobSettingsService;

  @Value("${treasury.jobs.wishpricechecker.runOnStartup:false}")
  private boolean runOnStartup;

  /**
   * Constructor for WishPriceJob.
   *
   * @param wishPriceService  the wish price service
   * @param jobSettingsService the job settings service
   */
  public WishPriceJob(WishPriceService wishPriceService,
                      JobSettingsService jobSettingsService) {
    this.wishPriceService = wishPriceService;
    this.jobSettingsService = jobSettingsService;
  }

  /**
   * Runs once after the application is fully started, if enabled.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void executeOnStartup() {
    if (!runOnStartup) {
      logger.info("WishPriceJob: runOnStartup deaktiviert");
      return;
    }
    if (!jobSettingsService.get().isWishPriceCheckerEnabled()) {
      logger.info("WishPriceJob: deaktiviert via Settings");
      return;
    }
    logger.info("Starte WishPriceJob nach ApplicationReadyEvent");
    processJob();
  }

  /**
   * Scheduled execution — every Monday at 08:00 by default.
   */
  @Scheduled(cron = "0 0 8 * * MON")
  public void execute() {
    if (!jobSettingsService.get().isWishPriceCheckerEnabled()) {
      logger.info("WishPriceJob: deaktiviert via Settings");
      return;
    }
    logger.info("Starte WishPriceJob (Scheduled)");
    processJob();
  }

  /**
   * Manual trigger, e.g. from the Settings UI.
   */
  public void triggerNow() {
    logger.info("WishPriceJob: triggerNow gestartet");
    processJob();
    logger.info("WishPriceJob: triggerNow beendet");
  }

  private void processJob() {
    try {
      wishPriceService.checkPricesAndNotify();
    } catch (Exception e) {
      logger.error("WishPriceJob fehlgeschlagen", e);
    }
  }
}
