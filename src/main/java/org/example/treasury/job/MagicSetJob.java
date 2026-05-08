package org.example.treasury.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.example.treasury.model.MagicSet;
import org.example.treasury.model.MagicSetScraperRun;
import org.example.treasury.repository.MagicSetScraperRunRepository;
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
 * Jeder Lauf wird als {@link MagicSetScraperRun} in der Datenbank protokolliert.
 */
@Component
public class MagicSetJob {

  private final ScryFallWebservice scryFallWebservice;
  private final MagicSetService magicSetService;
  private final JobSettingsService jobSettingsService;
  private final MagicSetScraperRunRepository magicSetScraperRunRepository;

  @Value("${treasury.jobs.magicset.runOnStartup:true}")
  private boolean runOnStartup;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for MagicSetJob.
   *
   * @param scryFallWebservice            the ScryFallWebservice instance
   * @param magicSetService               the MagicSetService instance
   * @param jobSettingsService            the JobSettingsService instance
   * @param magicSetScraperRunRepository  repository for run-history logging
   */
  public MagicSetJob(ScryFallWebservice scryFallWebservice, MagicSetService magicSetService,
      JobSettingsService jobSettingsService,
      MagicSetScraperRunRepository magicSetScraperRunRepository) {
    this.scryFallWebservice = scryFallWebservice;
    this.magicSetService = magicSetService;
    this.jobSettingsService = jobSettingsService;
    this.magicSetScraperRunRepository = magicSetScraperRunRepository;
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

  /**
   * Manuelles Triggern (z.B. wenn der Job in Settings aktiviert wird).
   */
  public void triggerNow() {
    if (!jobSettingsService.get().isMagicSetEnabled()) {
      logger.info("MagicSetJob: triggerNow gestartet (Hinweis: Job ist in Settings deaktiviert)");
    } else {
      logger.info("MagicSetJob: triggerNow gestartet");
    }
    processJob();
    logger.info("MagicSetJob: triggerNow beendet");
  }

  private void processJob() {
    Instant start = Instant.now();
    try {
      List<MagicSet> sets = scryFallWebservice.getSetList();
      magicSetService.saveAllMagicSets(sets);
      long durationMs = Duration.between(start, Instant.now()).toMillis();
      logger.info("MagicSetJob: {} Sets gespeichert in {}ms", sets.size(), durationMs);
      magicSetScraperRunRepository.save(MagicSetScraperRun.builder()
          .timestamp(start)
          .setsTotal(sets.size())
          .success(true)
          .durationMs(durationMs)
          .build());
    } catch (Exception e) {
      logger.error("MagicSetJob fehlgeschlagen", e);
      long durationMs = Duration.between(start, Instant.now()).toMillis();
      magicSetScraperRunRepository.save(MagicSetScraperRun.builder()
          .timestamp(start)
          .setsTotal(0)
          .success(false)
          .errorMessage(e.getMessage())
          .durationMs(durationMs)
          .build());
    }
  }
}