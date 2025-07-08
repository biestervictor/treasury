package org.example.treasury.job;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.example.treasury.model.Display;
import org.example.treasury.service.DisplayPriceCollectorService;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
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
  private final DisplayService displayService;
  private final MagicSetService magicSetService;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for MagicSetJob.
   *
   * @param displayPriceCollectorService the ScryFallWebservice instance
   * @param displayService               the displayService instance
   */
  public PriceScraperJob(DisplayPriceCollectorService displayPriceCollectorService,
                         DisplayService displayService, MagicSetService magicSetService) {
    this.displayPriceCollectorService = displayPriceCollectorService;
    this.displayService = displayService;
    this.magicSetService = magicSetService;
  }

  /**
   * Method that runs after the bean is initialized.
   */
  //TODO Fixen
  @PostConstruct
  public void executeOnStartup() {


    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(() -> {
      logger.info("Starte Scraper Job 1 Minute nach Start");
      processJob();
      scheduler.shutdown();
    }, 1, TimeUnit.MINUTES);
  }

  /**
   * Scheduled method that runs every day at midnight.
   * It fetches the Cardmarket prices.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {

    logger.info("Starte  Scraper Job");
    processJob();
  }

  private void processJob() {
    try (Playwright playwright = Playwright.create()) {


      Browser browser =
          playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
      Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
          .setUserAgent(
              "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

      BrowserContext context = browser.newContext(contextOptions);
      //Release von Set Boostern
      LocalDate releaseOfDraftBoosters = magicSetService.getMagicSetByCode("znr").getFirst()
          .getReleaseDate();
      List<String> setCodesUsed = new ArrayList<>();
      for (Display display : displayService.getAllDisplays()) {
        if (!setCodesUsed.contains(display.getSetCode() + display.getType())) {

          setCodesUsed.add(display.getSetCode() + display.getType());
          boolean isLegacy=  magicSetService.getMagicSetByCode(display.getSetCode()).getFirst().getReleaseDate()
              .isBefore(releaseOfDraftBoosters);


            displayPriceCollectorService.runScraper(context, display,isLegacy);


        }

      }

    } catch (Exception e) {
      logger.error("Job fehlgeschlagen", e);
    }
  }
}