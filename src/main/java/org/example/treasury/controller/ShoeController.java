package org.example.treasury.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.treasury.model.Shoe;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.EbayPriceCollectorService;
import org.example.treasury.service.ShoePriceCollectorService;
import org.example.treasury.service.ShoeService;
import org.example.treasury.service.StockxPriceCollectorService;
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
 * It provides endpoints for inserting, listing, scraping Klekt prices, and managing shoes.
 */

@Controller
@RequestMapping("/api/shoe")

public class ShoeController {

  private static final Logger logger = LoggerFactory.getLogger(ShoeController.class);

  private final CsvImporter csvImporter;
  private final ShoeService shoeService;
  private final ShoePriceCollectorService shoePriceCollectorService;
  private final EbayPriceCollectorService ebayPriceCollectorService;
  private final StockxPriceCollectorService stockxPriceCollectorService;

  /**
   * Constructor for ShoeController.
   *
   * @param csvImporter                 the CsvImporter instance
   * @param shoeService                 the ShoeService instance
   * @param shoePriceCollectorService   the ShoePriceCollectorService instance
   * @param ebayPriceCollectorService   the EbayPriceCollectorService instance
   * @param stockxPriceCollectorService the StockxPriceCollectorService instance
   */
  public ShoeController(CsvImporter csvImporter, ShoeService shoeService,
                        ShoePriceCollectorService shoePriceCollectorService,
                        EbayPriceCollectorService ebayPriceCollectorService,
                        StockxPriceCollectorService stockxPriceCollectorService) {
    this.csvImporter = csvImporter;
    this.shoeService = shoeService;
    this.shoePriceCollectorService = shoePriceCollectorService;
    this.ebayPriceCollectorService = ebayPriceCollectorService;
    this.stockxPriceCollectorService = stockxPriceCollectorService;
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
   * Update the StockX slug for a shoe.
   *
   * @param id                 shoe id
   * @param stockxSlug         the StockX product slug
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/updateStockxSlug")
  public String updateStockxSlug(
      @RequestParam String id,
      @RequestParam String stockxSlug,
      RedirectAttributes redirectAttributes) {
    shoeService.updateStockxSlug(id, stockxSlug);
    redirectAttributes.addFlashAttribute("message", "StockX-Slug gespeichert!");
    return "redirect:/api/shoe/list";
  }

  /**
   * Scrape the current Klekt price for a single shoe and update it in the database.
   *
   * @param id                 shoe id
   * @param redirectAttributes for flash messages
   * @return redirect to list
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

    if (shoe.getKlektSlug() == null || shoe.getKlektSlug().isBlank()) {
      redirectAttributes.addFlashAttribute("error",
          "Kein Klekt-Slug gesetzt für: " + shoe.getName());
      return "redirect:/api/shoe/list";
    }

    try {
      Optional<ShoePriceCollectorService.KlektPriceData> prices =
          shoePriceCollectorService.fetchPrices(shoe);
      if (prices.isPresent()) {
        ShoePriceCollectorService.KlektPriceData p = prices.get();
        shoeService.updateKlektPrices(id, p.ask(), p.bid());
        redirectAttributes.addFlashAttribute("message",
            String.format("Klekt aktualisiert: Ask %.2f € / Bid %.2f €  (%s %s)",
                p.ask(), p.bid(), shoe.getName(), shoe.getTyp()));
      } else {
        redirectAttributes.addFlashAttribute("error",
            "Kein Angebot auf Klekt gefunden für: " + shoe.getName()
                + " US " + shoe.getUsSize());
      }
    } catch (Exception e) {
      logger.error("Fehler beim Klekt-Scraping für {}: {}", shoe.getName(), e.getMessage());
      redirectAttributes.addFlashAttribute("error",
          "Scraping-Fehler: " + e.getMessage());
    }

    return "redirect:/api/shoe/list";
  }

  /**
   * Scrape Klekt prices for ALL shoes that have a klektSlug set.
   *
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/scrapeAllKlekt")
  public String scrapeAllKlekt(RedirectAttributes redirectAttributes) {
    List<Shoe> shoes = shoeService.getAllShoes();
    int updated = 0;
    int skipped = 0;
    int errors = 0;
    List<String> details = new ArrayList<>();

    for (Shoe shoe : shoes) {
      String label = shoe.getName() + " US " + shoe.getUsSize();
      if (shoe.getKlektSlug() == null || shoe.getKlektSlug().isBlank()) {
        skipped++;
        details.add("– " + label + ": kein Slug gesetzt");
        continue;
      }
      try {
        Optional<ShoePriceCollectorService.KlektPriceData> prices =
            shoePriceCollectorService.fetchPrices(shoe);
        if (prices.isPresent()) {
          ShoePriceCollectorService.KlektPriceData p = prices.get();
          shoeService.updateKlektPrices(shoe.getId(), p.ask(), p.bid());
          updated++;
          String bidInfo = p.bid() > 0
              ? String.format(" | Bid %.2f €", p.bid())
              : " | kein Bid";
          details.add(String.format("✓ %s: Ask %.2f €%s", label, p.ask(), bidInfo));
        } else {
          skipped++;
          details.add("– " + label + ": kein Angebot gefunden");
        }
        // Polite delay between requests
        Thread.sleep(1500);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        logger.error("Scraping-Fehler für {}: {}", shoe.getName(), e.getMessage());
        errors++;
        details.add("✗ " + label + ": Fehler – " + e.getMessage());
      }
    }

    redirectAttributes.addFlashAttribute("message",
        String.format("Klekt Scraping: %d aktualisiert, %d übersprungen, %d Fehler",
            updated, skipped, errors));
    redirectAttributes.addFlashAttribute("scrapeDetails", details);
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

    // Gewinn/Verlust bei Verkäufen
    model.addAttribute("totalWinSold", formattedTotalWinSold);
    model.addAttribute("totalWinSoldValue", totalWinSold);
    model.addAttribute("ebayConfigured", ebayPriceCollectorService.isConfigured());
    // StockX braucht keinen API-Key – Scraping ist immer aktiv
    model.addAttribute("stockxEnabled", true);

    return "shoe";
  }

  /**
   * Scrapt den aktuellen eBay-Preis für einen einzelnen Schuh.
   *
   * @param id                 shoe id
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/{id}/scrapeEbay")
  public String scrapeEbay(
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

    try {
      Optional<Double> price = ebayPriceCollectorService.fetchLowestPrice(shoe);
      if (price.isPresent()) {
        shoeService.updateEbayPrice(id, price.get());
        redirectAttributes.addFlashAttribute("message",
            String.format("eBay-Preis aktualisiert: %.2f €  (%s US %s)",
                price.get(), shoe.getName(), shoe.getUsSize()));
      } else {
        redirectAttributes.addFlashAttribute("error",
            "Kein eBay-Angebot gefunden für: " + shoe.getName() + " US " + shoe.getUsSize());
      }
    } catch (Exception e) {
      logger.error("eBay-Fehler für {}: {}", shoe.getName(), e.getMessage());
      redirectAttributes.addFlashAttribute("error", "eBay-Fehler: " + e.getMessage());
    }
    return "redirect:/api/shoe/list";
  }

  /**
   * Scrapt eBay-Preise für alle Schuhe.
   *
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/scrapeAllEbay")
  public String scrapeAllEbay(RedirectAttributes redirectAttributes) {
    if (!ebayPriceCollectorService.isConfigured()) {
      redirectAttributes.addFlashAttribute("error",
          "eBay App-ID nicht konfiguriert. Bitte ebay.app.id in application.properties setzen.");
      return "redirect:/api/shoe/list";
    }

    List<Shoe> shoes = shoeService.getAllShoes();
    int updated = 0;
    int skipped = 0;
    int errors = 0;
    List<String> details = new ArrayList<>();

    for (Shoe shoe : shoes) {
      String label = shoe.getName() + " US " + shoe.getUsSize();
      try {
        Optional<Double> price = ebayPriceCollectorService.fetchLowestPrice(shoe);
        if (price.isPresent()) {
          shoeService.updateEbayPrice(shoe.getId(), price.get());
          updated++;
          details.add(String.format("✓ %s: %.2f €", label, price.get()));
        } else {
          skipped++;
          details.add("– " + label + ": kein Angebot gefunden");
        }
        Thread.sleep(500);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        errors++;
        logger.error("eBay-Fehler für {}: {}", shoe.getName(), e.getMessage());
        details.add("✗ " + label + ": Fehler – " + e.getMessage());
      }
    }

    redirectAttributes.addFlashAttribute("message",
        String.format("eBay Scraping: %d aktualisiert, %d übersprungen, %d Fehler",
            updated, skipped, errors));
    redirectAttributes.addFlashAttribute("scrapeDetails", details);
    return "redirect:/api/shoe/list";
  }

  /**
   * Scrapt den aktuellen StockX-Ask/Bid für einen einzelnen Schuh.
   *
   * @param id                 shoe id
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/{id}/scrapeStockx")
  public String scrapeStockx(
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

    if (shoe.getStockxSlug() == null || shoe.getStockxSlug().isBlank()) {
      redirectAttributes.addFlashAttribute("error",
          "Kein StockX-Slug gesetzt für: " + shoe.getName());
      return "redirect:/api/shoe/list";
    }

    try {
      Optional<StockxPriceCollectorService.StockxPriceData> prices =
          stockxPriceCollectorService.fetchPrices(shoe);
      if (prices.isPresent()) {
        StockxPriceCollectorService.StockxPriceData p = prices.get();
        shoeService.updateStockxPrices(id, p.lowestAsk(), p.highestBid());
        redirectAttributes.addFlashAttribute("message",
            String.format("StockX aktualisiert: Ask %.2f € / Bid %.2f €  (%s %s)",
                p.lowestAsk(), p.highestBid(), shoe.getName(), shoe.getTyp()));
      } else {
        redirectAttributes.addFlashAttribute("error",
            "Keine StockX-Daten gefunden für: " + shoe.getName() + " US " + shoe.getUsSize());
      }
    } catch (Exception e) {
      logger.error("StockX-Fehler für {}: {}", shoe.getName(), e.getMessage());
      redirectAttributes.addFlashAttribute("error", "StockX-Fehler: " + e.getMessage());
    }
    return "redirect:/api/shoe/list";
  }

  /**
   * Scrapt StockX-Preise für alle Schuhe mit gesetztem stockxSlug.
   *
   * @param redirectAttributes for flash messages
   * @return redirect to list
   */
  @PostMapping("/scrapeAllStockx")
  public String scrapeAllStockx(RedirectAttributes redirectAttributes) {
    List<Shoe> shoes = shoeService.getAllShoes();
    int updated = 0;
    int skipped = 0;
    int errors = 0;
    List<String> details = new ArrayList<>();

    for (Shoe shoe : shoes) {
      String label = shoe.getName() + " US " + shoe.getUsSize();
      if (shoe.getStockxSlug() == null || shoe.getStockxSlug().isBlank()) {
        skipped++;
        details.add("– " + label + ": kein Slug gesetzt");
        continue;
      }
      try {
        Optional<StockxPriceCollectorService.StockxPriceData> prices =
            stockxPriceCollectorService.fetchPrices(shoe);
        if (prices.isPresent()) {
          StockxPriceCollectorService.StockxPriceData p = prices.get();
          shoeService.updateStockxPrices(shoe.getId(), p.lowestAsk(), p.highestBid());
          updated++;
          String bidInfo = p.highestBid() > 0
              ? String.format(" | Bid %.2f €", p.highestBid())
              : " | kein Bid";
          details.add(String.format("✓ %s: Ask %.2f €%s", label, p.lowestAsk(), bidInfo));
        } else {
          skipped++;
          details.add("– " + label + ": keine Daten gefunden");
        }
        Thread.sleep(2000);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        errors++;
        logger.error("StockX-Fehler für {}: {}", shoe.getName(), e.getMessage());
        details.add("✗ " + label + ": Fehler – " + e.getMessage());
      }
    }

    redirectAttributes.addFlashAttribute("message",
        String.format("StockX Scraping: %d aktualisiert, %d übersprungen, %d Fehler",
            updated, skipped, errors));
    redirectAttributes.addFlashAttribute("scrapeDetails", details);
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
