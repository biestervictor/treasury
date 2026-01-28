package org.example.treasury.controller;

import org.example.treasury.service.JobSettingsService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Stellt globale Model-Attribute fr alle Views bereit (z.B. Navbar Badges).
 */
@ControllerAdvice
public class NavStatusAdvice {

  private final JobSettingsService jobSettingsService;

  public NavStatusAdvice(JobSettingsService jobSettingsService) {
    this.jobSettingsService = jobSettingsService;
  }

  @ModelAttribute("jobsAllEnabled")
  public boolean jobsAllEnabled() {
    var s = jobSettingsService.get();
    return s.isSellEnabled() && s.isPriceScraperEnabled() && s.isMagicSetEnabled();
  }

  @ModelAttribute("jobsAnyDisabled")
  public boolean jobsAnyDisabled() {
    return !jobsAllEnabled();
  }
}
