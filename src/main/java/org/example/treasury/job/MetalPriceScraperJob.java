package org.example.treasury.job;

import org.example.treasury.service.EdelmetallService;
import org.example.treasury.service.JobSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Job: lädt regelmäßig Gold-/Silberpreise via Scraper und persistiert
 * (über EdelmetallService -> updatePricesAndStoreValuation()).
 *
 * <p>Analog zum SellJob / PriceScraperJob ist er per Settings (Runtime Toggle) deaktivierbar.</p>
 */
@Component
public class MetalPriceScraperJob {

  private static final Logger log = LoggerFactory.getLogger(MetalPriceScraperJob.class);

  private final EdelmetallService edelmetallService;
  private final JobSettingsService jobSettingsService;

  @Value("${treasury.jobs.metalpricescraper.runOnStartup:true}")
  private boolean runOnStartup;

  /** Startup delay in minutes (default: 5). */
  @Value("${treasury.jobs.metalpricescraper.startupDelayMinutes:5}")
  private long startupDelayMinutes;

  public MetalPriceScraperJob(EdelmetallService edelmetallService,
                              JobSettingsService jobSettingsService) {
    this.edelmetallService = edelmetallService;
    this.jobSettingsService = jobSettingsService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void executeOnStartup() {
    if (!jobSettingsService.get().isMetalPriceScraperEnabled()) {
      log.info("MetalPriceScraperJob: deaktiviert via Settings (treasury.jobs.metalPriceScraperEnabled=false)");
      return;
    }
    if (!runOnStartup) {
      log.info("MetalPriceScraperJob: runOnStartup deaktiviert (treasury.jobs.metalpricescraper.runOnStartup=false)");
      return;
    }

    long delayMin = Math.max(0, startupDelayMinutes);
    log.info("MetalPriceScraperJob: starte einmalig nach Startup mit Delay={}min", delayMin);

    // Einfacher Ansatz analog zu anderen Jobs: Delay via Thread.sleep
    // (Kein Scheduling-Executor nötig, damit der Job-Code kompakt bleibt.)
    new Thread(() -> {
      try {
        Thread.sleep(delayMin * 60_000L);
        runOnce("startup");
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }, "metal-price-scraper-startup").start();
  }

  /**
   * Läuft regelmäßig.
   * Default: alle 6 Stunden zur vollen Stunde.
   */
  @Scheduled(cron = "0 0 */6 * * *")
  public void execute() {
    if (!jobSettingsService.get().isMetalPriceScraperEnabled()) {
      log.info("MetalPriceScraperJob: deaktiviert via Settings (treasury.jobs.metalPriceScraperEnabled=false)");
      return;
    }
    runOnce("scheduled");
  }

  /**
   * Manuelles Triggern (z.B. wenn der Job in Settings aktiviert wird).
   */
  public void triggerNow() {
    if (!jobSettingsService.get().isMetalPriceScraperEnabled()) {
      log.info("MetalPriceScraperJob: triggerNow gestartet (Hinweis: Job ist in Settings deaktiviert)");
    }
    runOnce("manual");
  }

  void runOnce(String mode) {
    try {
      log.info("MetalPriceScraperJob: Starte Run ({})", mode);
      edelmetallService.updatePricesAndStoreValuation();
      log.info("MetalPriceScraperJob: Run beendet ({})", mode);
    } catch (Exception e) {
      // EdelmetallService ist bereits defensiv (kein Persist bei Fehlern),
      // aber wir loggen trotzdem, damit man es im Job-Log sieht.
      log.warn("MetalPriceScraperJob: Run fehlgeschlagen ({}) - {}", mode, e.getMessage());
    }
  }
}

