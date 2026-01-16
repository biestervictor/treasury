package org.example.treasury.controller;

import java.util.List;
import org.example.treasury.model.Shoe;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.ShoeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ShoeController is a Spring MVC controller that handles requests related to shoes.
 * It provides endpoints for inserting and listing shoes.
 */

@Controller
@RequestMapping("/api/shoe")

public class ShoeController {

  private final CsvImporter csvImporter;
  private final ShoeService shoeService;

  /**
   * Constructor for ShoeController.
   *
   * @param csvImporter the CsvImporter instance
   * @param shoeService the ShoeService instance
   */
  public ShoeController(CsvImporter csvImporter, ShoeService shoeService) {
    this.csvImporter = csvImporter;
    this.shoeService = shoeService;
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

    return "shoe";
  }

}