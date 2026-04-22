package org.example.treasury.controller;

import org.example.treasury.dto.DashboardDto;
import org.example.treasury.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * DashboardController stellt das globale Asset-Dashboard bereit.
 * Zeigt eine Übersicht aller Asset-Kategorien mit Werten, Gewinnen und Verlierern.
 */
@Controller
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  /**
   * Konstruktor mit DashboardService.
   *
   * @param dashboardService der Dashboard-Service
   */
  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * Gibt das globale Asset-Dashboard zurück.
   *
   * @param model Spring MVC Model
   * @return Name der Dashboard-View
   */
  @GetMapping
  public String dashboard(Model model) {
    DashboardDto dashboard = dashboardService.getDashboard();
    model.addAttribute("dashboard", dashboard);
    return "dashboard";
  }
}
