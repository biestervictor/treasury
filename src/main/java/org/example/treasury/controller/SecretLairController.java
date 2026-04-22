package org.example.treasury.controller;

import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.example.treasury.dto.AssetGroupDto;
import org.example.treasury.dto.PriceSnapshotDto;
import org.example.treasury.model.AggregatedSecretLair;
import org.example.treasury.model.SecretLair;
import org.example.treasury.service.CardMarketPriceHistoryService;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.SecretLairPriceCollectorService;
import org.example.treasury.service.SecretLairService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for Secret Lair operations.
 */
@Controller
@RequestMapping("/api/secretlair")
public class SecretLairController {

  private final CsvImporter csvImporter;
  private final SecretLairService secretLairService;
  private final CardMarketPriceHistoryService priceHistoryService;

  @Autowired
  private SecretLairPriceCollectorService secretLairPriceCollectorService;

  /**
   * Constructor for SecretLairController.
   *
   * @param csvImporter         csvImporter
   * @param secretLairService   secretLairService
   * @param priceHistoryService price history service
   */
  public SecretLairController(CsvImporter csvImporter,
                              SecretLairService secretLairService,
                              CardMarketPriceHistoryService priceHistoryService) {
    this.csvImporter = csvImporter;
    this.secretLairService = secretLairService;
    this.priceHistoryService = priceHistoryService;
  }

  /**
   * Shows the aggregated Secret Lair overview grouped by name.
   *
   * @param model the Spring MVC model
   * @return view name
   */
  @GetMapping("/aggregated")
  public String getAggregatedSecretLairs(Model model) {
    List<AggregatedSecretLair> aggregatedData = secretLairService.getAggregatedSecretLairs();

    double totalExpenses = secretLairService.getAllSecretLairs().stream()
        .filter(sl -> !sl.isSold())
        .mapToDouble(SecretLair::getValueBought)
        .sum();
    double currentValue = secretLairService.getAllSecretLairs().stream()
        .filter(sl -> !sl.isSold())
        .mapToDouble(SecretLair::getCurrentValue)
        .sum();

    model.addAttribute("aggregatedData", aggregatedData);
    model.addAttribute("totalExpenses", totalExpenses);
    model.addAttribute("currentValue", currentValue);
    return "aggregatedSecretLairs";
  }

  /**
   * Add a new Secret Lair.
   *
   * @param newSecretLair the new Secret Lair to add
   * @return redirect to insert page
   */
  @PostMapping("/add")
  public String addSecretLair(@ModelAttribute SecretLair newSecretLair) {
    secretLairService.addSecretLair(newSecretLair);
    return "redirect:/api/secretLair/insert";
  }

  /**
   * Insert displays from CSV file.
   *
   * @param soldOnly       show only sold items
   * @param isSelling      show only items being sold
   * @param highProfitOnly show only items with current value &gt; 1.5x purchase price
   * @param model          für das frontend
   * @return response
   */
  @GetMapping("/insert")
  public String insertSecretLair(
      @RequestParam(value = "soldOnly", required = false, defaultValue = "false") String soldOnly,
      @RequestParam(value = "isSelling", required = false, defaultValue = "false") String isSelling,
      @RequestParam(value = "highProfitOnly", required = false,
          defaultValue = "false") String highProfitOnly,
      Model model) {
    List<SecretLair> secretLairs = getSecretLairs(secretLairService.getAllSecretLairs());
    double sumEinkaufspreis = secretLairs.stream()
        .filter(sl -> !sl.isSold())
        .mapToDouble(SecretLair::getValueBought)
        .sum();
    double sumAktuellerWert = secretLairs.stream()
        .filter(sl -> !sl.isSold())
        .mapToDouble(SecretLair::getCurrentValue)
        .sum();
    double sumGewinn = secretLairs.stream()
        .filter(SecretLair::isSold)
        .mapToDouble(sl -> sl.getSoldPrice() - sl.getValueBought())
        .sum();

    secretLairs = secretLairs.stream()
        .filter(sl -> sl.isSold() == Boolean.parseBoolean(soldOnly)).toList();
    secretLairs = secretLairs.stream()
        .filter(sl -> sl.isSelling() == Boolean.parseBoolean(isSelling)).toList();

    boolean filterHighProfitOnly = "true".equalsIgnoreCase(highProfitOnly);
    if (filterHighProfitOnly) {
      secretLairs = secretLairs.stream()
          .filter(sl -> sl.getCurrentValue() > sl.getValueBought() * 1.5)
          .toList();
    }

    model.addAttribute("soldOnly", soldOnly);
    model.addAttribute("highProfitOnly", filterHighProfitOnly);
    model.addAttribute("isSelling", isSelling);
    model.addAttribute("sumEinkaufspreis", sumEinkaufspreis);
    model.addAttribute("sumAktuellerWert", sumAktuellerWert);
    model.addAttribute("sumGewinn", sumGewinn);
    model.addAttribute("secretlair", secretLairs);
    model.addAttribute("newSecretLair", new SecretLair());

    return "secretlair";
  }

  /**
   * Returns the price history for a Secret Lair item.
   *
   * @param id the Secret Lair MongoDB document ID
   * @return list of price snapshots ordered by date ascending
   */
  @GetMapping("/{id}/history")
  @ResponseBody
  public List<PriceSnapshotDto> getSecretLairHistory(@PathVariable String id) {
    return priceHistoryService.getHistory(id).stream()
        .map(s -> new PriceSnapshotDto(s.getDate().toString(), s.getPrice()))
        .toList();
  }

  private @Nullable List<SecretLair> getSecretLairs(List<SecretLair> secretLairs) {
    if (secretLairs.isEmpty()) {
      System.out.println("secretLairs is empty");
      secretLairs = csvImporter.importSecretLairCsv("src/main/resources/SecretLair.csv");
      secretLairService.saveAllSecretLairs(secretLairs);
      try (Playwright playwright = Playwright.create()) {
        secretLairPriceCollectorService.runScraper(playwright, secretLairs);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return secretLairs;
  }

  /**
   * Returns group information for a Secret Lair: all active positions with the same name.
   *
   * @param id the Secret Lair MongoDB document ID
   * @return AssetGroupDto for the group, or 404 if the Secret Lair is not found
   */
  @GetMapping("/{id}/group")
  @ResponseBody
  public ResponseEntity<AssetGroupDto> getSecretLairGroup(@PathVariable String id) {
    SecretLair sl = secretLairService.findById(id).orElse(null);
    if (sl == null) {
      return ResponseEntity.notFound().build();
    }
    List<SecretLair> siblings = secretLairService.findActiveByName(sl.getName());
    List<AssetGroupDto.AssetItemDto> items = siblings.stream()
        .map(s -> new AssetGroupDto.AssetItemDto(
            s.getId(), s.getLocation(), s.getValueBought(), s.getCurrentValue(),
            s.getCurrentValue() - s.getValueBought(), s.getLanguage(), s.getUrl()))
        .toList();
    double currentPrice = siblings.isEmpty() ? 0 : siblings.get(0).getCurrentValue();
    return ResponseEntity.ok(new AssetGroupDto(
        sl.getName(), "Secret Lair", currentPrice,
        "/api/secretlair/" + id + "/history",
        "/api/secretlair/" + id + "/price",
        items));
  }

  /**
   * Updates the current market price for all active Secret Lairs with the same name.
   *
   * @param id    any Secret Lair document ID within the target group
   * @param price the new market price in EUR
   * @return 200 OK on success, 404 if the given Secret Lair is not found
   */
  @PatchMapping("/{id}/price")
  @ResponseBody
  public ResponseEntity<Void> updateSecretLairGroupPrice(
      @PathVariable String id, @RequestParam double price) {
    SecretLair sl = secretLairService.findById(id).orElse(null);
    if (sl == null) {
      return ResponseEntity.notFound().build();
    }
    List<SecretLair> siblings = secretLairService.findActiveByName(sl.getName());
    LocalDate today = LocalDate.now();
    siblings.forEach(s -> {
      s.setCurrentValue(price);
      s.setUpdatedAt(today);
    });
    secretLairService.saveAllSecretLairs(siblings);
    return ResponseEntity.ok().build();
  }

  /**
   * Triggers the Cardmarket scraper for a single Secret Lair and returns the scraped price.
   *
   * @param id the Secret Lair document ID
   * @return 200 with {"price": X.XX}, 404 if not found, 500 on scrape error
   */
  @PostMapping("/{id}/scrape")
  @ResponseBody
  public ResponseEntity<Map<String, Double>> scrapeSecretLairPrice(@PathVariable String id) {
    SecretLair sl = secretLairService.findById(id).orElse(null);
    if (sl == null) {
      return ResponseEntity.notFound().build();
    }
    try (Playwright playwright = Playwright.create()) {
      secretLairPriceCollectorService.runScraper(playwright, List.of(sl));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
    SecretLair updated = secretLairService.findById(id).orElse(sl);
    return ResponseEntity.ok(Map.of("price", updated.getCurrentValue()));
  }

  /**
   * Update Secret Lair fields via form submission.
   *
   * @param id          the Secret Lair ID
   * @param location    new location
   * @param currentValue new current value
   * @param isSold      whether it is sold
   * @param selling     whether it is being sold
   * @param soldPrice   the sold price
   * @param dateBought  the date it was bought
   * @return redirect to insert page
   */
  @PostMapping("/update")
  public String updateSecretLair(@RequestParam String id,
                                 @RequestParam String location,
                                 @RequestParam Double currentValue,
                                 @RequestParam(required = false,
                                     defaultValue = "false") boolean isSold,
                                 @RequestParam(required = false,
                                     defaultValue = "false") boolean selling,
                                 @RequestParam(required = false, defaultValue = "0") Double soldPrice,
                                 @RequestParam("dateBought")
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate dateBought) {
    secretLairService.updateSecretLair(id, location, currentValue, isSold, selling,
        soldPrice, dateBought);
    return "redirect:/api/secretlair/insert";
  }
}
