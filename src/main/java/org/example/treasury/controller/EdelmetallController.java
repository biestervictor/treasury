package org.example.treasury.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.example.treasury.dto.ManualMetalPricesRequest;
import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.job.CollectorCoinPriceJob;
import org.example.treasury.model.CollectorCoinPrice;
import org.example.treasury.model.CollectorCoinPriceSource;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.repository.PreciousMetalRepository;
import org.example.treasury.service.CollectorCoinPricingService;
import org.example.treasury.service.EdelmetallService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/api/edelmetall")
public class EdelmetallController {

  private final EdelmetallService edelmetallService;
  private final CollectorCoinPricingService collectorCoinPricingService;
  private final CollectorCoinPriceJob collectorCoinPriceJob;
  private final PreciousMetalRepository preciousMetalRepository;

  /**
   * Konstruktor.
   *
   * @param edelmetallService           Edelmetall-Service
   * @param collectorCoinPricingService Sammlermünz-Preisservice
   * @param collectorCoinPriceJob       Sammlermünz-Preisermittlungsjob
   * @param preciousMetalRepository     Münz-Repository
   */
  public EdelmetallController(EdelmetallService edelmetallService,
                               CollectorCoinPricingService collectorCoinPricingService,
                               CollectorCoinPriceJob collectorCoinPriceJob,
                               PreciousMetalRepository preciousMetalRepository) {
    this.edelmetallService = edelmetallService;
    this.collectorCoinPricingService = collectorCoinPricingService;
    this.collectorCoinPriceJob = collectorCoinPriceJob;
    this.preciousMetalRepository = preciousMetalRepository;
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
      String msg = "Automatische Preisermittlung ist aktuell nicht verfügbar – "
          + "es wurde nichts gespeichert. Details: " + e.getMessage();
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

  /**
   * Manuelle Preiseingabe mit View-Redirect.
   */
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
   * Setzt den Sammlerwert (EUR/Stk) für eine einzelne Münze und löst sofort
   * einen neuen Valuation-Snapshot aus. Redirect zum Dashboard.
   *
   * @param id          MongoDB-ID der Münze
   * @param marketValue Sammlerwert pro Stück in EUR; 0.0 = zurücksetzen (Fallback auf Spot)
   */
  @PostMapping("/metals/{id}/marketValue")
  public ResponseEntity<String> setMarketValue(
      @PathVariable String id,
      @RequestParam double marketValue) {
    edelmetallService.updateMarketValue(id, marketValue);
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Market value updated");
  }

  /**
   * Preisverlauf-Seite für eine einzelne Münze (Thymeleaf).
   *
   * @param id    MongoDB-ID der Münze
   * @param model Spring MVC Model
   * @return Template-Name
   */
  @GetMapping("/metals/{id}/history")
  public String priceHistory(@PathVariable String id, Model model) {
    PreciousMetal metal = preciousMetalRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Münze nicht gefunden: " + id));
    List<CollectorCoinPrice> history = collectorCoinPricingService.getPriceHistory(id);
    model.addAttribute("metal", metal);
    model.addAttribute("history", history);
    model.addAttribute("sources", CollectorCoinPriceSource.values());
    return "metalPriceHistory";
  }

  /**
   * Triggert Sammlermünz-Preisermittlung für eine Quelle im Hintergrund. Redirect zum Dashboard.
   *
   * @param source Quelle (MA_SHOPS, EBAY, COININVEST, NUMISTA)
   */
  @PostMapping("/collector-prices/update")
  public ResponseEntity<String> updateCollectorPrices(
      @RequestParam CollectorCoinPriceSource source) {
    new Thread(() -> collectorCoinPriceJob.triggerSource(source),
        "collector-price-" + source).start();
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Gestartet: " + source.getDisplayName());
  }

  /**
   * Triggert Sammlermünz-Preisermittlung für alle Quellen im Hintergrund. Redirect zum Dashboard.
   */
  @PostMapping("/collector-prices/update/all")
  public ResponseEntity<String> updateAllCollectorPrices() {
    new Thread(() -> collectorCoinPriceJob.triggerAll(), "collector-price-all").start();
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Gestartet: alle Quellen");
  }

  /**
   * Dashboard View (Thymeleaf) mit Chart.
   */
  @GetMapping("/dashboard/view")
  public String dashboardView(Model model) {
    MetalDashboardDto dto = edelmetallService.getDashboard();
    model.addAttribute("dashboard", dto);
    model.addAttribute("sources", CollectorCoinPriceSource.values());
    return "edelmetallDashboard";
  }
}
