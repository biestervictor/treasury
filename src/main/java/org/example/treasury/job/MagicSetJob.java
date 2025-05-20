package org.example.treasury.job;

import javax.annotation.PostConstruct;
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
  Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for MagicSetJob.
   *
   * @param scryFallWebservice the ScryFallWebservice instance
   */
  public MagicSetJob(ScryFallWebservice scryFallWebservice) {
    this.scryFallWebservice = scryFallWebservice;
  }

  /**
   * Method that runs after the bean is initialized.
   */
  @PostConstruct
  public void executeOnStartup() {
    try {
      logger.info("Starte Job direkt nach dem Start");
      scryFallWebservice.getSetList();
    } catch (Exception e) {
      logger.error("Job fehlgeschlagen", e);
    }
  }

  /**
   * Scheduled method that runs every day at midnight.
   * It fetches the set list from the ScryFall web service.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {
    try {
      logger.info("Starte Job");
      scryFallWebservice.getSetList();
    } catch (Exception e) {
      logger.error("Job fehlgeschlagen");

    }
  }
}