package org.example.treasury.controller;

import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.List;
import org.example.treasury.dto.PriceSnapshotDto;
import org.example.treasury.model.SecretLair;
import org.example.treasury.service.CardMarketPriceHistoryService;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.SecretLairPriceCollectorService;
import org.example.treasury.service.SecretLairService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
