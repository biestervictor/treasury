package org.example.treasury.controller;

import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.dto.ManualMetalPricesRequest;
import org.example.treasury.service.EdelmetallService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/api/edelmetall")
public class EdelmetallController {

  private final EdelmetallService edelmetallService;

  public EdelmetallController(EdelmetallService edelmetallService) {
    this.edelmetallService = edelmetallService;
  }

  /**
   * Startet den CSV Import.
   */
  @PostMapping("/import")
  @ResponseBody
  public ResponseEntity<String> importCsv() {
    int imported = edelmetallService.importEdelmetallFromCSV();
    return ResponseEntity.ok("Imported: " + imported);
  }

  /**
   * Trigger für Preisupdate.
   */
  @PostMapping("/prices/update")
  public ResponseEntity<String> updatePrices() {
    try {
      edelmetallService.updatePricesAndStoreValuation();
      return ResponseEntity.status(303)
          .header("Location", "/api/edelmetall/dashboard/view")
          .body("Valuation snapshot stored");
    } catch (Exception e) {
      String msg = "Automatische Preisermittlung ist aktuell nicht verfügbar – es wurde nichts gespeichert. Details: "
          + e.getMessage();
      String encoded = UriUtils.encodeQueryParam(msg, StandardCharsets.UTF_8);
      return ResponseEntity.status(303)
          .header("Location", "/api/edelmetall/dashboard/view?error=" + encoded)
          .body(msg);
    }
  }

  /**
   * Manuelle Preiseingabe (ohne externen API-Call). Persistiert PriceSnapshot + Valuation für heute.
   */
  @PostMapping("/prices/manual")
  @ResponseBody
  public MetalDashboardDto manualPrices(@RequestBody ManualMetalPricesRequest request) {
    edelmetallService.storeManualPricesAndValuation(request);
    return edelmetallService.getDashboard();
  }

  @PostMapping("/prices/manual/view")
  public ResponseEntity<String> manualPricesView(@RequestBody ManualMetalPricesRequest request) {
    edelmetallService.storeManualPricesAndValuation(request);
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Valuation snapshot stored");
  }

  /**
   * REST API fürs Dashboard (JSON).
   */
  @GetMapping("/dashboard")
  @ResponseBody
  public MetalDashboardDto dashboard() {
    return edelmetallService.getDashboard();
  }

  /**
   * Dashboard View (Thymeleaf) mit Chart.
   */
  @GetMapping("/dashboard/view")
  public String dashboardView(Model model) {
    MetalDashboardDto dto = edelmetallService.getDashboard();
    model.addAttribute("dashboard", dto);
    return "edelmetallDashboard";
  }
}

