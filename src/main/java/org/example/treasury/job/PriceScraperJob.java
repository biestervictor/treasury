package org.example.treasury.job;


import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.example.treasury.model.Display;
import org.example.treasury.model.SecretLair;
import org.example.treasury.service.DisplayPriceCollectorService;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.SecretLairPriceCollectorService;
import org.example.treasury.service.SecretLairService;
import org.example.treasury.service.JobSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MagicSetJob is a class that contains a scheduled job to fetch data from the ScryFall web service.
 */

@Component

public class PriceScraperJob {

  private final DisplayPriceCollectorService displayPriceCollectorService;
  private final SecretLairPriceCollectorService secretLairPriceCollectorService;
  private final DisplayService displayService;
  private final MagicSetService magicSetService;
  private final SecretLairService secretLairService;
  private final JobSettingsService jobSettingsService;

  @Value("${treasury.jobs.pricescraper.runOnStartup:true}")
  private boolean runOnStartup;

  /** Startup delay in minutes (default: 200). */
  @Value("${treasury.jobs.pricescraper.startupDelayMinutes:200}")
  private long startupDelayMinutes;

  Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for MagicSetJob.
   *
   * @param displayPriceCollectorService the ScryFallWebservice instance
   * @param displayService               the displayService instance
   */
  public PriceScraperJob(DisplayPriceCollectorService displayPriceCollectorService,
                         SecretLairPriceCollectorService secretLairPriceCollectorService,
                         DisplayService displayService, MagicSetService magicSetService,
                         SecretLairService secretLairService,
                         JobSettingsService jobSettingsService) {
    this.displayPriceCollectorService = displayPriceCollectorService;
    this.secretLairPriceCollectorService = secretLairPriceCollectorService;
    this.displayService = displayService;
    this.magicSetService = magicSetService;
    this.secretLairService = secretLairService;
    this.jobSettingsService = jobSettingsService;
  }

  /**
   * Runs once after the application is fully started (optional).
   */
  @EventListener(ApplicationReadyEvent.class)
  public void executeOnStartup() {
    if (!jobSettingsService.get().isPriceScraperEnabled()) {
      logger.info("PriceScraperJob: deaktiviert via Settings (treasury.jobs.priceScraperEnabled=false)");
      return;
    }
    if (!runOnStartup) {
      logger.info("PriceScraperJob: runOnStartup deaktiviert (treasury.jobs.pricescraper.runOnStartup=false)");
      return;
    }

    long delay = Math.max(0, startupDelayMinutes);


    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(() -> {
      try {
        logger.info("PriceScraperJob: starte einmalig nach Startup mit Delay={}min", delay);
        processDisplayJob();
        processSecretLairJob();
      } finally {
        scheduler.shutdown();
      }
    }, delay, TimeUnit.MINUTES);
  }

  /**
   * Scheduled method that runs every day at midnight.
   * It fetches the Cardmarket prices.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {
    if (!jobSettingsService.get().isPriceScraperEnabled()) {
      logger.info("PriceScraperJob: deaktiviert via Settings (treasury.jobs.priceScraperEnabled=false)");
      return;
    }

    logger.info("Starte  Scraper Job");
    processSecretLairJob();
    processDisplayJob();
  }

  private void processSecretLairJob() {
    List<SecretLair> secretLairs = secretLairService.getAllSecretLairs();
    Collections.shuffle(secretLairs);
    if (secretLairs.isEmpty()) {
      secretLairService.saveAllSecretLairs(secretLairs);
    }
    try (Playwright playwright = Playwright.create()) {
      secretLairPriceCollectorService.runScraper(playwright, secretLairs);
    } catch (Exception e) {
      logger.error("PriceScraperJob: SecretLair Scraper fehlgeschlagen", e);
    }

  }

  private void processDisplayJob() {
    try (Playwright playwright = Playwright.create()) {

      //Release von Set Boostern
      LocalDate releaseOfDraftBoosters = magicSetService.getMagicSetByCode("ZNR").getFirst()
          .getReleaseDate();
      List<String> setCodesUsed = new ArrayList<>();
      List<Display> displays = displayService.getAllDisplays();

      Collections.shuffle(displays);
      for (Display display : displays) {

        if (!setCodesUsed.contains(display.getSetCode() + display.getType()+display.getLanguage())) {

          setCodesUsed.add(display.getSetCode() + display.getType());
          boolean isLegacy = magicSetService.getMagicSetByCode(display.getSetCode()).getFirst().getReleaseDate()
              .isBefore(releaseOfDraftBoosters);


          displayPriceCollectorService.runScraper(playwright, display, isLegacy);


        }

      }

    } catch (Exception e) {
      logger.error("Job fehlgeschlagen", e);
    }
    logger.info("Scraper Job ist beendet");
  }
}