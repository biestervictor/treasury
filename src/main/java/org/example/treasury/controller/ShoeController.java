package org.example.treasury.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.treasury.model.Shoe;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.ShoePriceCollectorService;
import org.example.treasury.service.ShoeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ShoeController is a Spring MVC controller that handles requests related to shoes.
 * It provides endpoints for inserting, listing, scraping Klekt prices,
 * and managing shoes.
 */

@Controller
@RequestMapping("/api/shoe")

public class ShoeController {

  private static final Logger logger = LoggerFactory.getLogger(ShoeController.class);

  private final CsvImporter csvImporter;
  private final ShoeService shoeService;
  private final ShoePriceCollectorService shoePriceCollectorService;

  /**
   * Constructor for ShoeController.
   *
   * @param csvImporter               the CsvImporter instance
   * @param shoeService               the ShoeService instance
   * @param shoePriceCollectorService the ShoePriceCollectorService instance
   */
  public ShoeController(CsvImporter csvImporter, ShoeService shoeService,
                        ShoePriceCollectorService shoePriceCollectorService) {
    this.csvImporter = csvImporter;
    this.shoeService = shoeService;
    this.shoePriceCollectorService = shoePriceCollectorService;
  }

  /**
   * Insert shoes from CSV file.
   *
   * @param model für das frontend
   * @return response
   */
  @GetMapping("/insert")
  public String insertShoes(Model model) {
    if (shoeService.getAllShoes().isEmpty()) {
      List<Shoe> shoes = csvImporter.importCsv("src/main/resources/Schuhe.csv");
      shoeService.saveAllShoes(shoes);
      model.addAttribute("shoe", shoes);
    } else {
      model.addAttribute("shoe", shoeService.getAllShoes());
    }
    return "shoe";
  }

  /**
   * Update the sold value for a shoe.
   *
   * @param id                 shoe id
   * @param valueSold          the sold value
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/updateValueSold")
  public String updateValueSold(
      @RequestParam String id,
      @RequestParam Double valueSold,
      RedirectAttributes redirectAttributes) {
    shoeService.updateValueSold(id, valueSold);
    redirectAttributes.addFlashAttribute("message", "Verkaufspreis gespeichert!");
    return "redirect:/api/shoe/list";
  }

  /**
   * Update the Klekt slug for a shoe.
   *
   * @param id                 shoe id
   * @param klektSlug          the Klekt product slug
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/updateKlektSlug")
  public String updateKlektSlug(
      @RequestParam String id,
      @RequestParam String klektSlug,
      RedirectAttributes redirectAttributes) {
    shoeService.updateKlektSlug(id, klektSlug);
    redirectAttributes.addFlashAttribute("message", "Klekt-Slug gespeichert!");
    return "redirect:/api/shoe/list";
  }

  /**
   * Get a list of all shoes.
   *
   * @param model für das frontend
   * @return response
   */
  @GetMapping("/list")
  public String getList(Model model) {
    List<Shoe> shoes = shoeService.getAllShoes();

    double totalValueBought = shoes.stream().mapToDouble(Shoe::getValueBought).sum();
    double totalValueStockX = shoes.stream().mapToDouble(Shoe::getValueStockX).sum();

    // Gewinn nur aus tatsächlichen Verkäufen (valueSold != 0)
    double totalWinSold = shoes.stream()
        .filter(s -> s.getValueSold() != 0)
        .mapToDouble(s -> s.getValueSold() - s.getValueBought())
        .sum();

    // Werte formatieren
    String formattedTotalValueBought = String.format("%.2f €", totalValueBought);
    String formattedTotalValueStockX = String.format("%.2f €", totalValueStockX);
    String formattedTotalWinSold = String.format("%.2f €", totalWinSold);

    model.addAttribute("shoe", shoes);
    model.addAttribute("totalValueBought", formattedTotalValueBought);
    model.addAttribute("totalValueStockX", formattedTotalValueStockX);
    model.addAttribute("totalWinSold", formattedTotalWinSold);
    model.addAttribute("totalWinSoldValue", totalWinSold);

    return "shoe";
  }

  /**
   * Scrapt Klekt-Preise (Ask + Bid) für alle Schuhe mit gesetztem Klekt-Slug.
   *
   * @param redirectAttributes für Flash-Nachrichten
   * @return Redirect zur Schuh-Liste
   */
  @PostMapping("/scrapeAllKlekt")
  public String scrapeAllKlekt(RedirectAttributes redirectAttributes) {
    List<Shoe> shoes = shoeService.getAllShoes();
    List<String> details = new ArrayList<>();
    int updated = 0;
    int failed = 0;

    for (Shoe shoe : shoes) {
      if (shoe.getKlektSlug() == null || shoe.getKlektSlug().isBlank()) {
        details.add("– " + shoe.getName() + ": kein Klekt-Slug");
        continue;
      }
      Optional<ShoePriceCollectorService.KlektPriceData> prices =
          shoePriceCollectorService.fetchPrices(shoe);
      if (prices.isPresent()) {
        shoeService.updateKlektPrices(shoe.getId(), prices.get().ask(), prices.get().bid());
        details.add(String.format("✓ %s: Ask %.2f €, Bid %.2f €",
            shoe.getName(), prices.get().ask(), prices.get().bid()));
        updated++;
      } else {
        details.add("✗ " + shoe.getName() + ": Fehler beim Abruf");
        failed++;
      }
    }

    redirectAttributes.addFlashAttribute("message",
        String.format("%d Schuhe aktualisiert, %d fehlgeschlagen", updated, failed));
    redirectAttributes.addFlashAttribute("scrapeDetails", details);
    return "redirect:/api/shoe/list";
  }

  /**
   * Scrapt Klekt-Preise (Ask + Bid) für einen einzelnen Schuh.
   *
   * @param id                 Schuh-ID
   * @param redirectAttributes für Flash-Nachrichten
   * @return Redirect zur Schuh-Liste
   */
  @PostMapping("/{id}/scrapeKlekt")
  public String scrapeKlekt(
      @PathVariable String id,
      RedirectAttributes redirectAttributes) {
    Shoe shoe = shoeService.getAllShoes().stream()
        .filter(s -> id.equals(s.getId()))
        .findFirst()
        .orElse(null);
    if (shoe == null) {
      redirectAttributes.addFlashAttribute("error", "Schuh nicht gefunden: " + id);
      return "redirect:/api/shoe/list";
    }
    Optional<ShoePriceCollectorService.KlektPriceData> prices =
        shoePriceCollectorService.fetchPrices(shoe);
    if (prices.isPresent()) {
      shoeService.updateKlektPrices(id, prices.get().ask(), prices.get().bid());
      redirectAttributes.addFlashAttribute("message",
          String.format("✓ %s: Ask %.2f €, Bid %.2f €",
              shoe.getName(), prices.get().ask(), prices.get().bid()));
    } else {
      redirectAttributes.addFlashAttribute("error",
          "Klekt-Preis konnte nicht abgerufen werden für: " + shoe.getName());
    }
    return "redirect:/api/shoe/list";
  }

  /**
   * Exportiert alle gesetzten Klekt-Slugs als JSON-Map (Name → Slug).
   * Dient zum Übertragen der Slugs in andere Umgebungen (z.B. Prod).
   *
   * @return Map von Schuhname zu klektSlug
   */
  @GetMapping("/slugExport")
  @ResponseBody
  public Map<String, String> exportSlugs() {
    return shoeService.getAllShoes().stream()
        .filter(s -> s.getKlektSlug() != null && !s.getKlektSlug().isBlank())
        .collect(Collectors.toMap(
            Shoe::getName,
            Shoe::getKlektSlug,
            (a, b) -> a));
  }

  /**
   * Importiert Klekt-Slugs aus einer JSON-Map (Name → Slug) und setzt sie
   * auf allen Schuhen mit passendem Namen. Bestehende Slugs werden überschrieben.
   *
   * @param slugMap Map von Schuhname zu klektSlug
   * @return Anzahl der aktualisierten Datensätze
   */
  @PostMapping("/slugImport")
  @ResponseBody
  public ResponseEntity<Map<String, Integer>> importSlugs(
      @RequestBody Map<String, String> slugMap) {
    List<Shoe> all = shoeService.getAllShoes();
    int updated = 0;
    for (Shoe shoe : all) {
      String slug = slugMap.get(shoe.getName());
      if (slug != null && !slug.isBlank()) {
        shoe.setKlektSlug(slug.trim());
        updated++;
      }
    }
    shoeService.saveAllShoes(all);
    logger.info("slugImport: {} Schuhe aktualisiert", updated);
    return ResponseEntity.ok(Map.of("updated", updated));
  }

}
