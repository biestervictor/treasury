package org.example.treasury.controller;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.example.treasury.model.Display;
import org.example.treasury.model.MagicSet;
import org.example.treasury.model.SecretLair;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.SecretLairPriceCollectorService;
import org.example.treasury.service.SecretLairService;
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
  public String insertSecretLair(@RequestParam(value = "soldOnly", required = false, defaultValue = "false") String soldOnly,
                                @RequestParam(value = "highProfitOnly", required = false, defaultValue = "false") String highProfitOnly,
                                Model model) {
   List<SecretLair> secretLairs = secretLairService.getAllSecretLairs();
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


    boolean filterHighProfitOnly = "true".equalsIgnoreCase(highProfitOnly);
    model.addAttribute("soldOnly", soldOnly);
    model.addAttribute("highProfitOnly", filterHighProfitOnly);

    // Filter anwenden

      secretLairs = secretLairs.stream().filter(sl -> sl.isSold()== Boolean.parseBoolean(soldOnly)).toList();

    if (filterHighProfitOnly) {
      secretLairs = secretLairs.stream()
          .filter(sl -> sl.getCurrentValue() > sl.getValueBought() * 1.5)
          .toList();
    }

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

    model.addAttribute("sumEinkaufspreis", sumEinkaufspreis);
    model.addAttribute("sumAktuellerWert", sumAktuellerWert);
    model.addAttribute("sumGewinn", sumGewinn);
    model.addAttribute("secretlair", secretLairs);
    model.addAttribute("newSecretLair", new SecretLair());

    return "secretlair";

  }
  @PostMapping("/update")
  public String updateSecretLair(@RequestParam String id,
                                 @RequestParam String location,
                                 @RequestParam Double currentValue,
                                 @RequestParam(required = false, defaultValue = "false") boolean isSold,
                                 @RequestParam("dateBought") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate dateBought) {
    secretLairService.updateSecretLair(id, location, currentValue, isSold, dateBought);
    return "redirect:/api/secretlair/insert";
  }
}