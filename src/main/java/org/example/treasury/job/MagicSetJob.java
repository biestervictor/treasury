package org.example.treasury.job;

import javax.annotation.PostConstruct;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.ScryFallWebservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MagicSetJob is a class that contains a scheduled job to fetch data from the ScryFall web service.
 */

@Component

public class MagicSetJob {

  private final ScryFallWebservice scryFallWebservice;
  private final MagicSetService magicSetService;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for MagicSetJob.
   *
   * @param scryFallWebservice the ScryFallWebservice instance
   */
  public MagicSetJob(ScryFallWebservice scryFallWebservice, MagicSetService magicSetService) {
    this.scryFallWebservice = scryFallWebservice;
    this.magicSetService = magicSetService;
  }

  /**
   * Method that runs after the bean is initialized.
   */
  @PostConstruct
  public void executeOnStartup() {

    logger.info("Starte MagicSet Job direkt nach dem Start");

    processJob();
  }

  /**
   * Scheduled method that runs every day at midnight.
   * It fetches the set list from the ScryFall web service.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {

    logger.info("Starte MagicSet Job");
    processJob();

  }

  private void processJob() {
    try {
      magicSetService.saveAllMagicSets(
          scryFallWebservice.getSetList());
    } catch (Exception e) {
      logger.error("Job fehlgeschlagen");

    }
  }
}