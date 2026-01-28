package org.example.treasury.job;

import org.example.treasury.service.JobSettingsService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.ScryFallWebservice;
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
public class MagicSetJob {

  private final ScryFallWebservice scryFallWebservice;
  private final MagicSetService magicSetService;
  private final JobSettingsService jobSettingsService;

  @Value("${treasury.jobs.magicset.runOnStartup:true}")
  private boolean runOnStartup;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for MagicSetJob.
   *
   * @param scryFallWebservice the ScryFallWebservice instance
   */
  public MagicSetJob(ScryFallWebservice scryFallWebservice, MagicSetService magicSetService,
      JobSettingsService jobSettingsService) {
    this.scryFallWebservice = scryFallWebservice;
    this.magicSetService = magicSetService;
    this.jobSettingsService = jobSettingsService;
  }

  /**
   * Runs once after the application is fully started.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void executeOnStartup() {
    if (!runOnStartup) {
      logger.info("MagicSetJob: runOnStartup deaktiviert (treasury.jobs.magicset.runOnStartup=false)");
      return;
    }
    if (!jobSettingsService.get().isMagicSetEnabled()) {
      logger.info("MagicSetJob: deaktiviert via Settings (treasury.jobs.magicSetEnabled=false)");
      return;
    }

    logger.info("Starte MagicSet Job nach ApplicationReadyEvent");
    processJob();
  }

  /**
   * Scheduled method that runs every day at midnight.
   * It fetches the set list from the ScryFall web service.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void execute() {
    if (!jobSettingsService.get().isMagicSetEnabled()) {
      logger.info("MagicSetJob: deaktiviert via Settings (treasury.jobs.magicSetEnabled=false)");
      return;
    }
    logger.info("Starte MagicSet Job (Scheduled)");
    processJob();
  }

  private void processJob() {
    try {
      magicSetService.saveAllMagicSets(scryFallWebservice.getSetList());
    } catch (Exception e) {
      logger.error("MagicSetJob fehlgeschlagen", e);
    }
  }
}