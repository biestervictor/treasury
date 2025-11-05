package org.example.treasury.job;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.example.treasury.model.Display;
import org.example.treasury.model.SecretLair;
import org.example.treasury.service.DisplayPriceCollectorService;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.SecretLairPriceCollectorService;
import org.example.treasury.service.SecretLairService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                         SecretLairService secretLairService) {
    this.displayPriceCollectorService = displayPriceCollectorService;
    this.secretLairPriceCollectorService = secretLairPriceCollectorService;
    this.displayService = displayService;
    this.magicSetService = magicSetService;
    this.secretLairService = secretLairService;
  }

  /**
   * Method that runs after the bean is initialized.
   */

  @PostConstruct
  public void executeOnStartup() {


    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(() -> {
      logger.info("Starte Scraper Job 200 Minute nach Start");
      processDisplayJob();
      processSecretLairJob();

      scheduler.shutdown();
    }, 200, TimeUnit.MINUTES);
  }

  /**
   * Scheduled method that runs every day at midnight.
   * It fetches the Cardmarket prices.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {

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
      e.printStackTrace();
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

        if (!setCodesUsed.contains(display.getSetCode() + display.getType())) {

          setCodesUsed.add(display.getSetCode() + display.getType());
          boolean isLegacy=  magicSetService.getMagicSetByCode(display.getSetCode()).getFirst().getReleaseDate()
              .isBefore(releaseOfDraftBoosters);


          displayPriceCollectorService.runScraper(playwright, display,isLegacy);


        }

      }

    } catch (Exception e) {
      logger.error("Job fehlgeschlagen", e);
    }
    logger.info("Scraper Job ist beendet");
  }
}