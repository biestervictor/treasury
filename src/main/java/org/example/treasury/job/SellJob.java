package org.example.treasury.job;

import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.example.treasury.dto.MailRequest;
import org.example.treasury.model.CardMarketModel;
import org.example.treasury.model.Display;
import org.example.treasury.model.SecretLair;
import org.example.treasury.service.DisplayPriceCollectorService;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.SecretLairPriceCollectorService;
import org.example.treasury.service.SecretLairService;
import org.example.treasury.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SellJob {
  private final DisplayPriceCollectorService displayPriceCollectorService;
  private final SecretLairPriceCollectorService secretLairPriceCollectorService;
  private final DisplayService displayService;
  private final MagicSetService magicSetService;
  private final SecretLairService secretLairService;



  @Value("${treasury.jobs.sell.runOnStartup:true}")
  private boolean runOnStartup;

  /** Startup delay in seconds (default: 60s). */
  @Value("${treasury.jobs.sell.startupDelaySeconds:60}")
  private long startupDelaySeconds;




  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public SellJob(DisplayPriceCollectorService displayPriceCollectorService,
                 SecretLairPriceCollectorService secretLairPriceCollectorService,
                 DisplayService displayService, MagicSetService magicSetService,
                 SecretLairService secretLairService
                 ) {
    this.displayPriceCollectorService = displayPriceCollectorService;
    this.secretLairPriceCollectorService = secretLairPriceCollectorService;
    this.displayService = displayService;
    this.magicSetService = magicSetService;
    this.secretLairService = secretLairService;
  }

  /**
   * Runs once after the application is fully started (optional).
   */
  @EventListener(ApplicationReadyEvent.class)
  public void executeOnStartup() {
    if (!runOnStartup) {
      logger.info("SellJob: runOnStartup deaktiviert (treasury.jobs.sell.runOnStartup=false)");
      return;
    }

    long delay = Math.max(0, startupDelaySeconds);




    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(() -> {
      try {
        logger.info("SellJob: starte einmalig nach Startup mit Delay={}s", delay);
        processDisplayJob();
        processSecretLairJob();


      } catch (Exception e) {
        logger.error("SellJob: Startup Run fehlgeschlagen", e);

      } finally {
        scheduler.shutdown();
      }
    }, delay, TimeUnit.SECONDS);

  }



  /**
   * Scheduled method that runs every day at midnight.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {
    logger.info("SellJob: Starte Scheduled Run");
    processSecretLairJob();
    processDisplayJob();
    logger.info("SellJob: Run beendet");
  }

  private void processSecretLairJob() {
    List<SecretLair> secretLairs = new ArrayList<>(
        secretLairService.getAllSecretLairs().stream().filter(CardMarketModel::isSelling).toList());
    Collections.shuffle(secretLairs);

    if (secretLairs.isEmpty()) {
      logger.info("SellJob: Keine SecretLairs zum Verkaufen vorhanden.");
      return;
    }

    try (Playwright playwright = Playwright.create()) {
      secretLairPriceCollectorService.runScraper(playwright, secretLairs);
    } catch (Exception e) {
      logger.error("SellJob: SecretLair Scraper fehlgeschlagen", e);
    }
  }

  private void processDisplayJob() {
    try (Playwright playwright = Playwright.create()) {
      // Release von Set Boostern
      LocalDate releaseOfDraftBoosters = magicSetService.getMagicSetByCode("ZNR").getFirst()
          .getReleaseDate();
      List<String> setCodesUsed = new ArrayList<>();
      List<Display> displays = new ArrayList<>(
          displayService.getAllDisplays().stream().filter(CardMarketModel::isSelling).toList());

      Collections.shuffle(displays);
      for (Display display : displays) {
        if (!setCodesUsed.contains(display.getSetCode() + display.getType())) {
          setCodesUsed.add(display.getSetCode() + display.getType());
          boolean isLegacy = magicSetService.getMagicSetByCode(display.getSetCode()).getFirst()
              .getReleaseDate().isBefore(releaseOfDraftBoosters);

          displayPriceCollectorService.runScraper(playwright, display, isLegacy);
        }
      }

    } catch (Exception e) {
      logger.error("SellJob: Display Scraper fehlgeschlagen", e);
    }

  }
}
