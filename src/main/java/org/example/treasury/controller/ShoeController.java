package org.example.treasury.controller;

import java.util.List;
import org.example.treasury.model.Shoe;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.ShoeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
    double totalWinStockX = totalValueStockX - totalValueBought;

    // Werte formatieren
    String formattedTotalValueBought = String.format("%.2f €", totalValueBought);
    String formattedTotalValueStockX = String.format("%.2f €", totalValueStockX);
    String formattedTotalWinStockX =
        String.format("%.2f € (%.2f%%)", totalWinStockX, (totalWinStockX / totalValueBought) * 100);

    model.addAttribute("shoe", shoes);
    model.addAttribute("totalValueBought", formattedTotalValueBought);
    model.addAttribute("totalValueStockX", formattedTotalValueStockX);
    model.addAttribute("totalWinStockX", formattedTotalWinStockX);

    return "shoe";
  }

}