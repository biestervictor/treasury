package org.example.treasury.controller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import org.example.treasury.dto.JobSettingsUpdateRequest;
import org.example.treasury.dto.MailRequest;
import org.example.treasury.model.JobKey;
import org.example.treasury.service.JobSettingsService;
import org.example.treasury.service.JobSettingsViewService;
import org.example.treasury.service.JobTriggerService;
import org.example.treasury.service.JobRuntimeSettingsService;
import org.example.treasury.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the settings page (job management and dev-only features).
 */
@Controller
@RequestMapping("/api/settings")
public class SettingsController {

  private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

  private final JobSettingsService jobSettingsService;
  private final JobSettingsViewService jobSettingsViewService;
  private final JobTriggerService jobTriggerService;
  private final JobRuntimeSettingsService jobRuntimeSettingsService;

  @Autowired(required = false)
  private MailService mailService;

  @Value("${treasury.dev-mode:false}")
  private boolean devMode;

  @Value("${treasury.mail.startup.to:victor.biester@icloud.com}")
  private String mailTo;

  /**
   * Constructor.
   *
   * @param jobSettingsService        the job settings service
   * @param jobSettingsViewService    the job settings view service
   * @param jobTriggerService         the job trigger service
   * @param jobRuntimeSettingsService the runtime settings service
   */
  public SettingsController(JobSettingsService jobSettingsService,
                            JobSettingsViewService jobSettingsViewService,
                            JobTriggerService jobTriggerService,
                            JobRuntimeSettingsService jobRuntimeSettingsService) {
    this.jobSettingsService = jobSettingsService;
    this.jobSettingsViewService = jobSettingsViewService;
    this.jobTriggerService = jobTriggerService;
    this.jobRuntimeSettingsService = jobRuntimeSettingsService;
  }

  /**
   * Renders the settings page.
   *
   * @param model the Spring MVC model
   * @return view name
   */
  @GetMapping
  public String settings(Model model) {
    model.addAttribute("settings", jobSettingsService.get());
    model.addAttribute("updatedAt", jobSettingsService.getUpdatedAt());
    model.addAttribute("devMode", devMode);

    var jobs = jobSettingsViewService.list();
    model.addAttribute("jobs", jobs);

    DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMANY)
        .withZone(ZoneId.systemDefault());

    model.addAttribute("nextRuns", jobs.stream().collect(java.util.stream.Collectors.toMap(
        j -> j.key(),
        j -> {
          Instant next = jobSettingsViewService.nextRun(j);
          return next != null ? fmt.format(next) : "-";
        }
    )));
    return "settings";
  }

  /**
   * Sends a test mail to the configured notification address.
   * Only available when {@code treasury.dev-mode=true}.
   *
   * @param redirectAttributes flash attributes for the redirect
   * @return redirect to settings page
   */
  @PostMapping("/sendTestMail")
  public String sendTestMail(RedirectAttributes redirectAttributes) {
    if (!devMode) {
      redirectAttributes.addFlashAttribute("success", "Testmail nicht verfügbar (kein Dev-Mode).");
      return "redirect:/api/settings";
    }
    if (mailService == null) {
      redirectAttributes.addFlashAttribute("success", "MailService nicht aktiv (mail.enabled=false).");
      return "redirect:/api/settings";
    }
    try {
      mailService.send(new MailRequest(
          List.of(mailTo),
          "[Treasury Dev] Testmail",
          "Dies ist eine Testmail vom Treasury Dev-System.\n\nMailversand funktioniert korrekt."));
      log.info("Testmail erfolgreich gesendet an {}", mailTo);
      redirectAttributes.addFlashAttribute("success", "Testmail gesendet an " + mailTo);
    } catch (Exception e) {
      log.warn("Testmail-Versand fehlgeschlagen: {}", e.getMessage());
      redirectAttributes.addFlashAttribute("success", "Testmail fehlgeschlagen: " + e.getMessage());
    }
    return "redirect:/api/settings";
  }

  /**
   * Updates job enable/disable flags and cron schedules.
   *
   * @param sellEnabled             whether the sell job is enabled
   * @param priceScraperEnabled     whether the price scraper job is enabled
   * @param magicSetEnabled         whether the magic set job is enabled
   * @param metalPriceScraperEnabled whether the metal price scraper is enabled
   * @param wishPriceCheckerEnabled whether the wish price checker is enabled
   * @param sellCron                cron expression for the sell job
   * @param priceScraperCron        cron expression for the price scraper
   * @param magicSetCron            cron expression for the magic set job
   * @param metalPriceScraperCron   cron expression for the metal price scraper
   * @param wishPriceCheckerCron    cron expression for the wish price checker
   * @param triggerJob              optional job key to trigger immediately
   * @param redirectAttributes      flash attributes for the redirect
   * @return redirect to settings page
   */
  @PostMapping("/jobs")
  public String updateJobs(
      @RequestParam(name = "sellEnabled", defaultValue = "false") boolean sellEnabled,
      @RequestParam(name = "priceScraperEnabled", defaultValue = "false") boolean priceScraperEnabled,
      @RequestParam(name = "magicSetEnabled", defaultValue = "false") boolean magicSetEnabled,
      @RequestParam(name = "metalPriceScraperEnabled", defaultValue = "false") boolean metalPriceScraperEnabled,
      @RequestParam(name = "wishPriceCheckerEnabled", defaultValue = "false") boolean wishPriceCheckerEnabled,
      @RequestParam(name = "sellCron", defaultValue = "") String sellCron,
      @RequestParam(name = "priceScraperCron", defaultValue = "") String priceScraperCron,
      @RequestParam(name = "magicSetCron", defaultValue = "") String magicSetCron,
      @RequestParam(name = "metalPriceScraperCron", defaultValue = "") String metalPriceScraperCron,
      @RequestParam(name = "wishPriceCheckerCron", defaultValue = "") String wishPriceCheckerCron,
      @RequestParam(name = "triggerJob", required = false) String triggerJob,
      RedirectAttributes redirectAttributes) {

    log.info("SettingsController.updateJobs called: sellEnabled={}, priceScraperEnabled={},"
            + " magicSetEnabled={}, metalPriceScraperEnabled={}, wishPriceCheckerEnabled={},"
            + " triggerJob={}",
        sellEnabled, priceScraperEnabled, magicSetEnabled, metalPriceScraperEnabled,
        wishPriceCheckerEnabled, triggerJob);

    var before = jobSettingsService.get();
    JobSettingsUpdateRequest req = new JobSettingsUpdateRequest(
        sellEnabled, priceScraperEnabled, magicSetEnabled, metalPriceScraperEnabled,
        wishPriceCheckerEnabled);

    jobSettingsService.update(req.sellEnabled(), req.priceScraperEnabled(), req.magicSetEnabled(),
        req.metalPriceScraperEnabled(), req.wishPriceCheckerEnabled());

    // Cron Updates persistieren (in-memory, später DB)
    Instant now = Instant.now();
    if (sellCron != null && !sellCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.SELL);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(
            JobKey.SELL, req.sellEnabled(), "0 0 0 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
          JobKey.SELL, req.sellEnabled(), sellCron.trim(), old.zoneId(), now));
    }
    if (priceScraperCron != null && !priceScraperCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.PRICE_SCRAPER);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(
            JobKey.PRICE_SCRAPER, req.priceScraperEnabled(), "0 0 0 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
          JobKey.PRICE_SCRAPER, req.priceScraperEnabled(), priceScraperCron.trim(), old.zoneId(), now));
    }
    if (magicSetCron != null && !magicSetCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.MAGIC_SET);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(
            JobKey.MAGIC_SET, req.magicSetEnabled(), "0 0 0 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
          JobKey.MAGIC_SET, req.magicSetEnabled(), magicSetCron.trim(), old.zoneId(), now));
    }
    if (metalPriceScraperCron != null && !metalPriceScraperCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.METAL_PRICE_SCRAPER);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(
            JobKey.METAL_PRICE_SCRAPER, req.metalPriceScraperEnabled(), "0 0 */6 * * *", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
          JobKey.METAL_PRICE_SCRAPER, req.metalPriceScraperEnabled(),
          metalPriceScraperCron.trim(), old.zoneId(), now));
    }
    if (wishPriceCheckerCron != null && !wishPriceCheckerCron.isBlank()) {
      var old = jobRuntimeSettingsService.get(JobKey.WISH_PRICE_CHECKER);
      if (old == null) {
        old = new org.example.treasury.model.JobRuntimeSettings(
            JobKey.WISH_PRICE_CHECKER, req.wishPriceCheckerEnabled(), "0 0 8 * * MON", null, now);
      }
      jobRuntimeSettingsService.upsert(new org.example.treasury.model.JobRuntimeSettings(
          JobKey.WISH_PRICE_CHECKER, req.wishPriceCheckerEnabled(),
          wishPriceCheckerCron.trim(), old.zoneId(), now));
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
    if (!before.isWishPriceCheckerEnabled() && req.wishPriceCheckerEnabled()) {
      jobTriggerService.trigger(JobKey.WISH_PRICE_CHECKER);
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

