package org.example.treasury.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.example.treasury.config.JobSettingsProperties;
import org.example.treasury.model.JobKey;
import org.example.treasury.model.JobSchedule;
import org.example.treasury.model.JobSetting;
import org.springframework.stereotype.Service;

/**
 * Baut eine UI-View über die aktuellen Job-Settings inkl. Schedule/NextRun.
 *
 * <p>Trennung vom Runtime-Storage: später kann JobSettingsService mit Persistenz ersetzt werden,
 * ohne die UI verändern zu müssen.</p>
 */
@Service
public class JobSettingsViewService {

  private final JobSettingsService jobSettingsService;
  private final JobScheduleService jobScheduleService;
  private final JobRuntimeSettingsService jobRuntimeSettingsService;

  public JobSettingsViewService(JobSettingsService jobSettingsService,
                                JobScheduleService jobScheduleService,
                                JobRuntimeSettingsService jobRuntimeSettingsService) {
    this.jobSettingsService = jobSettingsService;
    this.jobScheduleService = jobScheduleService;
    this.jobRuntimeSettingsService = jobRuntimeSettingsService;
  }

  public List<JobSetting> list() {
    Instant updatedAt = jobSettingsService.getUpdatedAt();
    var sell = jobRuntimeSettingsService.get(JobKey.SELL);
    var price = jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER);
    var magic = jobRuntimeSettingsService.get(JobKey.MAGIC_SET);
    var metal = jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER);

    List<JobSetting> jobs = new ArrayList<>();
    jobs.add(new JobSetting(JobKey.SELL, "SellJob", sell.enabled(),
        JobSchedule.cron(sell.cron(), "täglich 00:00"), null, updatedAt));
    jobs.add(new JobSetting(JobKey.PRICE_SCRAPER, "PriceScraperJob", price.enabled(),
        JobSchedule.cron(price.cron(), "täglich 00:00"), null, updatedAt));
    jobs.add(new JobSetting(JobKey.MAGIC_SET, "MagicSetJob", magic.enabled(),
        JobSchedule.cron(magic.cron(), "täglich 00:00"), null, updatedAt));
    jobs.add(new JobSetting(JobKey.METAL_PRICE_SCRAPER, "EdelmetallPreisScraperJob",
        metal.enabled(), JobSchedule.cron(metal.cron(), "alle 6 Stunden"), null, updatedAt));

    // order stable
    jobs.sort(Comparator.comparing(j -> j.key().ordinal()));
    return jobs;
  }

  public Instant nextRun(JobSetting setting) {
    return jobScheduleService.nextRun(setting.schedule(), Instant.now());
  }
}

