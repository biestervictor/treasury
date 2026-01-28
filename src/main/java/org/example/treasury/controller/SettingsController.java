package org.example.treasury.controller;

import org.example.treasury.service.JobSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/api/settings")
public class SettingsController {

  private final JobSettingsService jobSettingsService;

  public SettingsController(JobSettingsService jobSettingsService) {
    this.jobSettingsService = jobSettingsService;
  }

  @GetMapping
  public String settings(Model model) {
    model.addAttribute("settings", jobSettingsService.get());
    model.addAttribute("updatedAt", jobSettingsService.getUpdatedAt());
    return "settings";
  }

  @PostMapping("/jobs")
  public String updateJobs(
      @RequestParam(name = "sellEnabled", defaultValue = "false") boolean sellEnabled,
      @RequestParam(name = "priceScraperEnabled", defaultValue = "false") boolean priceScraperEnabled,
      @RequestParam(name = "magicSetEnabled", defaultValue = "false") boolean magicSetEnabled,
      RedirectAttributes redirectAttributes) {

    jobSettingsService.update(sellEnabled, priceScraperEnabled, magicSetEnabled);
    redirectAttributes.addFlashAttribute("success", "Job-Einstellungen gespeichert.");
    return "redirect:/api/settings";
  }
}
