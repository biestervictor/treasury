package org.example.treasury.controller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.example.treasury.dto.JobSettingsUpdateRequest;
import org.example.treasury.model.JobKey;
import org.example.treasury.service.JobSettingsService;
import org.example.treasury.service.JobSettingsViewService;
import org.example.treasury.service.JobTriggerService;
import org.example.treasury.service.JobRuntimeSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/api/settings")
public class SettingsController {

  private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

  private final JobSettingsService jobSettingsService;
  private final JobSettingsViewService jobSettingsViewService;
  private final JobTriggerService jobTriggerService;
  private final JobRuntimeSettingsService jobRuntimeSettingsService;

  public SettingsController(JobSettingsService jobSettingsService,
                            JobSettingsViewService jobSettingsViewService,
                            JobTriggerService jobTriggerService,
                            JobRuntimeSettingsService jobRuntimeSettingsService) {
    this.jobSettingsService = jobSettingsService;
    this.jobSettingsViewService = jobSettingsViewService;
    this.jobTriggerService = jobTriggerService;
    this.jobRuntimeSettingsService = jobRuntimeSettingsService;
  }

  @GetMapping
  public String settings(Model model) {
    model.addAttribute("settings", jobSettingsService.get());
    model.addAttribute("updatedAt", jobSettingsService.getUpdatedAt());

    var jobs = jobSettingsViewService.list();
    model.addAttribute("jobs", jobs);

    DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMANY)
        .withZone(ZoneId.systemDefault());

    // einfache Map: JobKey -> nextRun String
    model.addAttribute("nextRuns", jobs.stream().collect(java.util.stream.Collectors.toMap(
        j -> j.key(),
        j -> {
          Instant next = jobSettingsViewService.nextRun(j);
          return next != null ? fmt.format(next) : "-";
        }
    )));
    return "settings";
  }

  @PostMapping("/jobs")
  public String updateJobs(
      @RequestParam(name = "sellEnabled", defaultValue = "false") boolean sellEnabled,
      @RequestParam(name = "priceScraperEnabled", defaultValue = "false") boolean priceScraperEnabled,
      @RequestParam(name = "magicSetEnabled", defaultValue = "false") boolean magicSetEnabled,
      @RequestParam(name = "metalPriceScraperEnabled", defaultValue = "false") boolean metalPriceScraperEnabled,
      @RequestParam(name = "sellCron", defaultValue = "") String sellCron,
      @RequestParam(name = "priceScraperCron", defaultValue = "") String priceScraperCron,
      @RequestParam(name = "magicSetCron", defaultValue = "") String magicSetCron,
      @RequestParam(name = "metalPriceScraperCron", defaultValue = "") String metalPriceScraperCron,
      @RequestParam(name = "triggerJob", required = false) String triggerJob,
      RedirectAttributes redirectAttributes) {

    log.info("SettingsController.updateJobs called: sellEnabled={}, priceScraperEnabled={}, magicSetEnabled={}, metalPriceScraperEnabled={}, triggerJob={} ",
        sellEnabled, priceScraperEnabled, magicSetEnabled, metalPriceScraperEnabled, triggerJob);

    // Diff-Logik: wenn ein Job von false -> true wechselt, sofort triggern.
    var before = jobSettingsService.get();
    JobSettingsUpdateRequest req = new JobSettingsUpdateRequest(
        sellEnabled, priceScraperEnabled, magicSetEnabled, metalPriceScraperEnabled);

    jobSettingsService.update(req.sellEnabled(), req.priceScraperEnabled(), req.magicSetEnabled(),
        req.metalPriceScraperEnabled());

    // Cron Updates persistieren (in-memory, später DB)
    Instant now = Instant.now();
    if (sellCron != null && !sellCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.SELL);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(JobKey.SELL, req.sellEnabled(), "0 0 0 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(JobKey.SELL,
          req.sellEnabled(), sellCron.trim(), old.zoneId(), now));
    }
    if (priceScraperCron != null && !priceScraperCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(JobKey.PRICE_SCRAPER, req.priceScraperEnabled(), "0 0 0 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(JobKey.PRICE_SCRAPER,
          req.priceScraperEnabled(), priceScraperCron.trim(), old.zoneId(), now));
    }
    if (magicSetCron != null && !magicSetCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.MAGIC_SET);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(JobKey.MAGIC_SET, req.magicSetEnabled(), "0 0 0 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(JobKey.MAGIC_SET,
          req.magicSetEnabled(), magicSetCron.trim(), old.zoneId(), now));
    }
    if (metalPriceScraperCron != null && !metalPriceScraperCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(JobKey.METAL_PRICE_SCRAPER, req.metalPriceScraperEnabled(), "0 0 */6 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(JobKey.METAL_PRICE_SCRAPER,
          req.metalPriceScraperEnabled(), metalPriceScraperCron.trim(), old.zoneId(), now));
    }

    if (!before.isSellEnabled() && req.sellEnabled()) {
      jobTriggerService.trigger(JobKey.SELL);
    }
    if (!before.isPriceScraperEnabled() && req.priceScraperEnabled()) {
      jobTriggerService.trigger(JobKey.PRICE_SCRAPER);
    }
    if (!before.isMagicSetEnabled() && req.magicSetEnabled()) {
      jobTriggerService.trigger(JobKey.MAGIC_SET);
    }
    if (!before.isMetalPriceScraperEnabled() && req.metalPriceScraperEnabled()) {
      jobTriggerService.trigger(JobKey.METAL_PRICE_SCRAPER);
    }

    // Manueller Sofort-Trigger via Button
    if (triggerJob != null && !triggerJob.isBlank()) {
      try {
        JobKey key = JobKey.valueOf(triggerJob.trim());
        jobTriggerService.trigger(key);
        redirectAttributes.addFlashAttribute("success", "Job wurde gestartet: " + key);
      } catch (IllegalArgumentException iae) {
        redirectAttributes.addFlashAttribute("success", "Unbekannter JobKey: " + triggerJob);
      }
      return "redirect:/api/settings";
    }

    redirectAttributes.addFlashAttribute("success", "Job-Einstellungen gespeichert.");
    return "redirect:/api/settings";
  }
}
