package org.example.treasury.controller;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    return "redirect:/api/secretlair/insert";
  }
  /**
   * Insert displays from CSV file.
   *
   * @param model für das frontend
   * @return response
   */
  @GetMapping("/insert")
  public String insertSecretLair(Model model) {
   List<SecretLair> secretLairs = secretLairService.getAllSecretLairs();
   if (secretLairs.isEmpty()) {
     secretLairs = csvImporter.importSecretLairCsv("src/main/resources/SecretLair.csv");
   secretLairService.saveAllSecretLairs(secretLairs);
     try (Playwright playwright = Playwright.create()) {


       Browser browser =
           playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
       Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
           .setUserAgent(
               "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

       BrowserContext context = browser.newContext(contextOptions);
     secretLairPriceCollectorService.runScraper(context, secretLairs);
       browser.close();
     } catch (Exception e) {
       e.printStackTrace();
     }
   }
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