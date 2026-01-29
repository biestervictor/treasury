package org.example.treasury.controller;

import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.List;
import org.example.treasury.model.SecretLair;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/secretlair")
public class SecretLairController {

  private final CsvImporter csvImporter;
  private final SecretLairService secretLairService;

  @Autowired
  private SecretLairPriceCollectorService secretLairPriceCollectorService;

  /**
   * Constructor for SecretLairController.
   *
   * @param csvImporter    csvImporter
   */
  public SecretLairController(CsvImporter csvImporter, SecretLairService secretLairService) {
    this.csvImporter = csvImporter;
    this.secretLairService = secretLairService;
  }
  @PostMapping("/add")
  public String addSecretLair(@ModelAttribute SecretLair newSecretLair) {
    secretLairService.addSecretLair(newSecretLair);
    return "redirect:/api/secretLair/insert";
  }
  /**
   * Insert displays from CSV file.
   *
   * @param model für das frontend
   * @return response
   */
  @GetMapping("/insert")
  public String insertSecretLair(@RequestParam(value = "soldOnly", required = false, defaultValue = "false") String soldOnly,@RequestParam(value = "isSelling", required = false, defaultValue = "false") String isSelling,
                                @RequestParam(value = "highProfitOnly", required = false, defaultValue = "false") String highProfitOnly,
                                Model model) {
   List<SecretLair> secretLairs =getSecretLairs(secretLairService.getAllSecretLairs());
    // Summen: nur nicht verkaufte berücksichtigen
    double sumEinkaufspreis = secretLairs.stream()
        .filter(sl -> !sl.isSold())
        .mapToDouble(SecretLair::getValueBought)
        .sum();
    double sumAktuellerWert = secretLairs.stream()
        .filter(sl -> !sl.isSold())
        .mapToDouble(SecretLair::getCurrentValue)
        .sum();

    // Gewinn auf verkauften
    double sumGewinn = secretLairs.stream()
        .filter(SecretLair::isSold)
        .mapToDouble(sl -> sl.getSoldPrice() - sl.getValueBought())
        .sum();




    // Filter anwenden
      secretLairs = secretLairs.stream().filter(sl -> sl.isSold()== Boolean.parseBoolean(soldOnly)).toList();
    secretLairs = secretLairs.stream().filter(sl -> sl.isSelling()== Boolean.parseBoolean(isSelling)).toList();

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

  @PostMapping("/update")
  public String updateSecretLair(@RequestParam String id,
                                 @RequestParam String location,
                                 @RequestParam Double currentValue,
                                 @RequestParam(required = false, defaultValue = "false") boolean isSold,
                                 @RequestParam(required = false, defaultValue = "false") boolean selling,
                                 @RequestParam(required = false, defaultValue = "0") Double soldPrice,
                                 @RequestParam("dateBought") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate dateBought) {
    secretLairService.updateSecretLair(id, location, currentValue, isSold, selling, soldPrice, dateBought);
    return "redirect:/api/secretlair/insert";
  }
}