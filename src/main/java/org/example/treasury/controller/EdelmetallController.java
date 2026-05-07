package org.example.treasury.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.treasury.dto.ManualMetalPricesRequest;
import org.example.treasury.dto.MetalDashboardDto;
import org.example.treasury.job.CollectorCoinPriceJob;
import org.example.treasury.model.CollectorCoinPrice;
import org.example.treasury.model.CollectorCoinPriceSource;
import org.example.treasury.model.CollectorScraperRun;
import org.example.treasury.model.Manufacturer;
import org.example.treasury.model.PreciousMetal;
import org.example.treasury.model.PreciousMetalType;
import org.example.treasury.repository.CollectorCoinPriceRepository;
import org.example.treasury.repository.PreciousMetalRepository;
import org.example.treasury.service.CollectorCoinPricingService;
import org.example.treasury.service.EdelmetallService;
import org.springframework.format.annotation.DateTimeFormat;
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

/**
 * Controller für Edelmetall-Dashboard, Preisermittlung, Sammlerwerte und Verwaltung.
 */
@Controller
@RequestMapping("/api/edelmetall")
public class EdelmetallController {

  private final EdelmetallService edelmetallService;
  private final CollectorCoinPricingService collectorCoinPricingService;
  private final CollectorCoinPriceJob collectorCoinPriceJob;
  private final PreciousMetalRepository preciousMetalRepository;
  private final CollectorCoinPriceRepository collectorCoinPriceRepository;

  /**
   * Konstruktor.
   *
   * @param edelmetallService             Edelmetall-Service
   * @param collectorCoinPricingService   Sammlermünz-Preisservice
   * @param collectorCoinPriceJob         Sammlermünz-Preisermittlungsjob
   * @param preciousMetalRepository       Münz-Repository
   * @param collectorCoinPriceRepository  Preisverlauf-Repository
   */
  public EdelmetallController(EdelmetallService edelmetallService,
                               CollectorCoinPricingService collectorCoinPricingService,
                               CollectorCoinPriceJob collectorCoinPriceJob,
                               PreciousMetalRepository preciousMetalRepository,
                               CollectorCoinPriceRepository collectorCoinPriceRepository) {
    this.edelmetallService = edelmetallService;
    this.collectorCoinPricingService = collectorCoinPricingService;
    this.collectorCoinPriceJob = collectorCoinPriceJob;
    this.preciousMetalRepository = preciousMetalRepository;
    this.collectorCoinPriceRepository = collectorCoinPriceRepository;
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
   * Manuelle Preiseingabe (ohne externen API-Call).
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
   * Aktualisiert alle editierbaren Felder einer Münze.
   *
   * @param id                  MongoDB-ID der Münze
   * @param name                Bezeichnung
   * @param type                Metalltyp (GOLD oder SILVER)
   * @param year                Erscheinungsjahr (optional)
   * @param manufacturer        Enum-Name des Herstellers (optional)
   * @param mintage             Auflage (optional)
   * @param weightInGrams       Gewicht in Gramm
   * @param quantity            Anzahl der Stück
   * @param purchasePrice       Kaufpreis pro Stück in EUR
   * @param importedAt          Kaufdatum
   * @param marketValue         Sammlerwert pro Stück (0 = zurücksetzen)
   * @param collectorSearchTerm Optionaler primärer Suchbegriff
   * @param searchTerms         Alternative Suchbegriffe (Newline/Semikolon getrennt)
   * @return 200 OK
   */
  @PostMapping("/metals/{id}/update")
  @ResponseBody
  public ResponseEntity<String> updateMetal(
      @PathVariable String id,
      @RequestParam String name,
      @RequestParam PreciousMetalType type,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) String manufacturer,
      @RequestParam(required = false) Integer mintage,
      @RequestParam double weightInGrams,
      @RequestParam int quantity,
      @RequestParam double purchasePrice,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate importedAt,
      @RequestParam(defaultValue = "0") double marketValue,
      @RequestParam(required = false) String collectorSearchTerm,
      @RequestParam(required = false, defaultValue = "") String searchTerms) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      metal.setName(name.trim());
      metal.setType(type);
      metal.setYear((year != null && year > 0) ? year : null);
      Manufacturer mfr = null;
      if (manufacturer != null && !manufacturer.isBlank()) {
        try {
          mfr = Manufacturer.valueOf(manufacturer.trim());
        } catch (IllegalArgumentException ignored) {
          // unbekannter Enum-Wert → null
        }
      }
      metal.setManufacturer(mfr);
      metal.setMintage((mintage != null && mintage > 0) ? mintage : null);
      metal.setWeightInGrams(weightInGrams);
      metal.setQuantity(quantity);
      metal.setPurchasePrice(purchasePrice);
      metal.setImportedAt(importedAt);
      metal.setMarketValue(marketValue);
      metal.setCollectorSearchTerm(
          collectorSearchTerm != null && !collectorSearchTerm.isBlank()
              ? collectorSearchTerm.trim() : null);
      java.util.List<String> termList = java.util.Arrays.stream(searchTerms.split("[\\n;]+"))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .distinct()
          .collect(java.util.stream.Collectors.toList());
      metal.setSearchTerms(termList);
      preciousMetalRepository.save(metal);
    });
    edelmetallService.recomputeValuation();
    return ResponseEntity.ok("Gespeichert");
  }

  /**
   * Benennt eine Münze um.
   *
   * @param id   MongoDB-ID der Münze
   * @param name neuer Name
   */
  @PostMapping("/metals/{id}/rename")
  @ResponseBody
  public ResponseEntity<String> renameMetal(
      @PathVariable String id,
      @RequestParam String name) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      metal.setName(name.trim());
      preciousMetalRepository.save(metal);
    });
    return ResponseEntity.ok("Name aktualisiert");
  }

  /**
   * Setzt den Hersteller einer Münze.
   *
   * @param id           MongoDB-ID der Münze
   * @param manufacturer Enum-Name des Herstellers (leer = zurücksetzen)
   */
  @PostMapping("/metals/{id}/manufacturer")
  @ResponseBody
  public ResponseEntity<String> setManufacturer(
      @PathVariable String id,
      @RequestParam(required = false) String manufacturer) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      if (manufacturer == null || manufacturer.isBlank()) {
        metal.setManufacturer(null);
      } else {
        try {
          metal.setManufacturer(Manufacturer.valueOf(manufacturer.trim()));
        } catch (IllegalArgumentException e) {
          metal.setManufacturer(null);
        }
      }
      preciousMetalRepository.save(metal);
    });
    return ResponseEntity.ok("Hersteller aktualisiert");
  }

  /**
   * Setzt die Auflage (Mintage) einer Münze.
   *
   * @param id      MongoDB-ID der Münze
   * @param mintage Auflage (Stückzahl); null oder 0 = zurücksetzen
   */
  @PostMapping("/metals/{id}/mintage")
  @ResponseBody
  public ResponseEntity<String> setMintage(
      @PathVariable String id,
      @RequestParam(required = false) Integer mintage) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      metal.setMintage((mintage != null && mintage > 0) ? mintage : null);
      preciousMetalRepository.save(metal);
    });
    return ResponseEntity.ok("Auflage aktualisiert");
  }

  /**
   * Übernimmt einen Scraper-Preis als neuen Sammlerwert und merkt sich
   * den zugehörigen Shop als bevorzugte Quelle für Auto-Updates.
   *
   * @param id    MongoDB-ID der Münze
   * @param shop  Display-Name der Preisquelle (z.B. "silber-corner.de")
   * @param price übernommener Preis in EUR
   */
  @PostMapping("/metals/{id}/accept-scrape-result")
  @ResponseBody
  public ResponseEntity<String> acceptScrapeResult(
      @PathVariable String id,
      @RequestParam String shop,
      @RequestParam double price) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      metal.setPreferredShop(shop);
      preciousMetalRepository.save(metal);
    });
    if (price > 0) {
      edelmetallService.updateMarketValue(id, price);
    }
    return ResponseEntity.ok("Übernommen");
  }

  /**
   * Setzt das Erscheinungsjahr einer Münze.
   *
   * @param id   MongoDB-ID der Münze
   * @param year neues Jahr (0 = zurücksetzen)
   */
  @PostMapping("/metals/{id}/year")
  @ResponseBody
  public ResponseEntity<String> setYear(
      @PathVariable String id,
      @RequestParam(required = false) Integer year) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      metal.setYear((year != null && year > 0) ? year : null);
      preciousMetalRepository.save(metal);
    });
    return ResponseEntity.ok("Jahr aktualisiert");
  }

  /**
   * Setzt die Suchbegriff-Liste einer Münze.
   * Mehrere Begriffe werden durch Newline oder Semikolon getrennt.
   *
   * @param id          MongoDB-ID der Münze
   * @param searchTerms Suchbegriffe, getrennt durch \\n oder ;
   */
  @PostMapping("/metals/{id}/search-terms")
  @ResponseBody
  public ResponseEntity<String> setSearchTerms(
      @PathVariable String id,
      @RequestParam(required = false, defaultValue = "") String searchTerms) {
    preciousMetalRepository.findById(id).ifPresent(metal -> {
      List<String> terms = java.util.Arrays.stream(searchTerms.split("[\\n;]+"))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .distinct()
          .collect(java.util.stream.Collectors.toList());
      metal.setSearchTerms(terms);
      preciousMetalRepository.save(metal);
    });
    return ResponseEntity.ok("Suchbegriffe aktualisiert");
  }

  /**
   * Startet den Scraper für eine einzelne Münze über alle Quellen asynchron.
   * Gibt sofort 202 zurück; Status abrufbar via GET /metals/{id}/scrape/status.
   *
   * @param id MongoDB-ID der Münze
   */
  @PostMapping("/metals/{id}/scrape")
  @ResponseBody
  public ResponseEntity<String> scrapeForMetal(@PathVariable String id) {
    if (!preciousMetalRepository.existsById(id)) {
      return ResponseEntity.notFound().build();
    }
    if (collectorCoinPricingService.isMetalScrapeRunning(id)) {
      return ResponseEntity.status(409).body("Scraper läuft bereits für diese Münze.");
    }
    new Thread(() -> collectorCoinPricingService.updateFromAllSourcesForMetal(id),
        "coin-scrape-" + id).start();
    return ResponseEntity.accepted().body("Gestartet");
  }

  /**
   * Liefert den aktuellen Scraper-Status für eine einzelne Münze als JSON.
   *
   * @param id MongoDB-ID der Münze
   */
  @GetMapping("/metals/{id}/scrape/status")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> metalScrapeStatus(@PathVariable String id) {
    Map<String, Object> status = collectorCoinPricingService.getMetalScrapeStatus(id);
    return ResponseEntity.ok(status);
  }

  /**
   *
   * @param id          MongoDB-ID der Münze
   * @param marketValue Sammlerwert pro Stück in EUR; 0.0 = zurücksetzen
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
   * Löscht eine Münze (PreciousMetal) sowie alle zugehörigen Preis-Einträge.
   * Löst anschließend eine Neubewertung aus.
   *
   * @param id MongoDB-ID der zu löschenden Münze
   */
  @PostMapping("/metals/{id}/delete")
  public ResponseEntity<String> deleteMetal(@PathVariable String id) {
    collectorCoinPriceRepository.deleteByPreciousMetalId(id);
    preciousMetalRepository.deleteById(id);
    edelmetallService.recomputeValuation();
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Gelöscht");
  }

  /**
   * Löscht einen einzelnen Preis-Eintrag aus der Scraper-History einer Münze.
   *
   * @param id      MongoDB-ID des CollectorCoinPrice-Eintrags
   * @param metalId MongoDB-ID der Münze (für den Redirect zurück zur History-Seite)
   */
  @PostMapping("/collector-prices/{id}/delete")
  public ResponseEntity<String> deleteCollectorPrice(
      @PathVariable String id,
      @RequestParam String metalId) {
    collectorCoinPriceRepository.deleteById(id);
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/metals/" + metalId + "/history")
        .body("Gelöscht");
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
   * Triggert Sammlermünz-Preisermittlung für eine Quelle im Hintergrund.
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
   * Triggert Sammlermünz-Preisermittlung für alle Quellen im Hintergrund.
   * Wenn bereits ein Lauf läuft, wird 409 zurückgegeben.
   */
  @PostMapping("/collector-prices/update/all")
  public ResponseEntity<String> updateAllCollectorPrices() {
    if (collectorCoinPricingService.isAllScraperRunning()) {
      return ResponseEntity.status(409).body("Scraper läuft bereits.");
    }
    new Thread(() -> collectorCoinPriceJob.triggerAll(), "collector-price-all").start();
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Gestartet: alle Quellen");
  }

  /**
   * Liefert den aktuellen Status des Gesamt-Scraper-Laufs als JSON.
   *
   * @return JSON mit running, completed, total, currentSource
   */
  @GetMapping("/collector-prices/status")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> scraperStatus() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("running", collectorCoinPricingService.isAllScraperRunning());
    status.put("completed", collectorCoinPricingService.getCompletedCount());
    status.put("total", collectorCoinPricingService.getTotalSources());
    status.put("currentSource", collectorCoinPricingService.getCurrentSourceName());
    return ResponseEntity.ok(status);
  }

  /**
   * Scraper-Verlauf-Seite (Thymeleaf): zeigt alle Läufe mit Erfolgsquoten und Preisänderungen.
   * Zeigt zusätzlich Münzen die noch nie von einem Scraper gefunden wurden.
   *
   * @param model Spring MVC Model
   * @return Template-Name
   */
  @GetMapping("/scraper-history")
  public String scraperHistory(Model model) {
    List<CollectorScraperRun> runs = collectorCoinPricingService.getScraperHistory();
    Map<CollectorCoinPriceSource, CollectorScraperRun> latestPerSource =
        collectorCoinPricingService.getLatestRunPerSource();
    // Münzen ohne jeglichen Preis-Eintrag
    List<PreciousMetal> allMetals = preciousMetalRepository.findAll();
    List<PreciousMetal> neverFoundMetals = allMetals.stream()
        .filter(m -> !collectorCoinPriceRepository.existsByPreciousMetalId(m.getId()))
        .sorted(java.util.Comparator.comparing(PreciousMetal::getName))
        .collect(java.util.stream.Collectors.toList());
    model.addAttribute("runs", runs);
    model.addAttribute("latestRunPerSource", latestPerSource);
    model.addAttribute("sources", CollectorCoinPriceSource.values());
    model.addAttribute("neverFoundMetals", neverFoundMetals);
    return "collectorScraperHistory";
  }

  /**
   * Zeigt das Formular zum manuellen Hinzufügen einer neuen Münze.
   *
   * @param model Spring MVC Model
   * @return Template-Name
   */
  @GetMapping("/metals/new")
  public String newMetalForm(Model model) {
    model.addAttribute("types", PreciousMetalType.values());
    model.addAttribute("manufacturers", Manufacturer.values());
    model.addAttribute("today", LocalDate.now().toString());
    return "addPreciousMetal";
  }

  /**
   * Speichert eine manuell eingetragene Münze und leitet zum Dashboard zurück.
   *
   * @param name                Bezeichnung der Münze
   * @param type                Metalltyp (GOLD oder SILVER)
   * @param year                Erscheinungsjahr (optional)
   * @param manufacturer        Enum-Name des Herstellers (optional)
   * @param mintage             Auflage/Limitierung (optional)
   * @param weightInGrams       Gewicht in Gramm
   * @param quantity            Anzahl der Stück
   * @param purchasePrice       Kaufpreis pro Stück in EUR
   * @param importedAt          Kaufdatum
   * @param marketValue         Sammlerwert pro Stück (0 = nicht gesetzt)
   * @param collectorSearchTerm Optionaler primärer Suchbegriff für den Scraper
   * @param searchTerms         Optionale alternative Suchbegriffe (Newline/Semikolon getrennt)
   * @return 303-Redirect zum Dashboard
   */
  @PostMapping("/metals")
  public ResponseEntity<String> addMetal(
      @RequestParam String name,
      @RequestParam PreciousMetalType type,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) String manufacturer,
      @RequestParam(required = false) Integer mintage,
      @RequestParam double weightInGrams,
      @RequestParam int quantity,
      @RequestParam double purchasePrice,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate importedAt,
      @RequestParam(defaultValue = "0") double marketValue,
      @RequestParam(required = false) String collectorSearchTerm,
      @RequestParam(required = false, defaultValue = "") String searchTerms) {
    java.util.List<String> termList = java.util.Arrays.stream(searchTerms.split("[\\n;]+"))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .distinct()
        .collect(java.util.stream.Collectors.toList());
    Manufacturer mfr = null;
    if (manufacturer != null && !manufacturer.isBlank()) {
      try {
        mfr = Manufacturer.valueOf(manufacturer.trim());
      } catch (IllegalArgumentException ignored) {
        // unbekannter Enum-Wert → null
      }
    }
    PreciousMetal metal = PreciousMetal.builder()
        .name(name.trim())
        .type(type)
        .year(year)
        .manufacturer(mfr)
        .mintage(mintage != null && mintage > 0 ? mintage : null)
        .weightInGrams(weightInGrams)
        .quantity(quantity)
        .purchasePrice(purchasePrice)
        .importedAt(importedAt)
        .marketValue(marketValue)
        .collectorSearchTerm(
            collectorSearchTerm != null && !collectorSearchTerm.isBlank()
                ? collectorSearchTerm.trim() : null)
        .searchTerms(termList)
        .build();
    metal.setImportKey(EdelmetallService.buildImportKey(metal));
    preciousMetalRepository.save(metal);
    edelmetallService.recomputeValuation();
    return ResponseEntity.status(303)
        .header("Location", "/api/edelmetall/dashboard/view")
        .body("Münze gespeichert");
  }

  /**
   * Dashboard View (Thymeleaf) mit Chart und abgeleitetem Sammlerwert pro Münze.
   *
   * @param model Spring MVC Model
   * @return Template-Name
   */
  @GetMapping("/dashboard/view")
  public String dashboardView(Model model) {
    MetalDashboardDto dto = edelmetallService.getDashboard();
    model.addAttribute("dashboard", dto);
    model.addAttribute("sources", CollectorCoinPriceSource.values());
    model.addAttribute("manufacturers", Manufacturer.values());
    model.addAttribute("latestCollectorPrices",
        collectorCoinPricingService.getLatestCollectorPricePerMetal(
            dto.currentPrices().goldEurPerOunce(),
            dto.currentPrices().silverEurPerOunce()));
    // Map metal-ID → PreciousMetal für Zugriff auf Jahr, Hersteller, Suchbegriffe im Template
    java.util.Map<String, PreciousMetal> metalMap = new java.util.HashMap<>();
    preciousMetalRepository.findAll().forEach(m -> metalMap.put(m.getId(), m));
    model.addAttribute("metals", metalMap);
    return "edelmetallDashboard";
  }
}
