package org.example.treasury.controller;

import org.example.treasury.service.JobSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Stellt globale Model-Attribute fr alle Views bereit (z.B. Navbar Badges).
 */
@ControllerAdvice
public class NavStatusAdvice {

  private final JobSettingsService jobSettingsService;

  private final String buildVersion;
  private final String buildTime;
  private final String mongodbUri;

  private static final String DEV_MONGODB_HOST = "mongodb-service.treasury.svc.cluster.local";

  public NavStatusAdvice(
      JobSettingsService jobSettingsService,
      @Value("${treasury.build.version:unknown}") String buildVersion,
      @Value("${treasury.build.time:unknown}") String buildTime,
      @Value("${spring.mongodb.uri:}") String mongodbUri) {
    this.jobSettingsService = jobSettingsService;
    this.buildVersion = buildVersion;
    this.buildTime = buildTime;
    this.mongodbUri = mongodbUri;
  }

  @ModelAttribute("jobsAllEnabled")
  public boolean jobsAllEnabled() {
    var s = jobSettingsService.get();
    return s.isSellEnabled() && s.isPriceScraperEnabled() && s.isMagicSetEnabled()
        && s.isMetalPriceScraperEnabled();
  }

  @ModelAttribute("jobsAnyDisabled")
  public boolean jobsAnyDisabled() {
    return !jobsAllEnabled();
  }

  @ModelAttribute("buildVersion")
  public String buildVersion() {
    return buildVersion;
  }

  @ModelAttribute("buildTime")
  public String buildTime() {
    return buildTime;
  }

  @ModelAttribute("mongodbUri")
  public String mongodbUri() {
    return mongodbUri;
  }

  @ModelAttribute("dbIsDev")
  public boolean dbIsDev() {
    return mongodbUri != null && mongodbUri.contains(DEV_MONGODB_HOST);
  }

  @ModelAttribute("dbIsProd")
  public boolean dbIsProd() {
    return !dbIsDev();
  }
}
