package org.example.treasury.controller;

import org.example.treasury.model.Shoe;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.ShoeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/api/shoe")
public class ShoeController {
    @Autowired
    CsvImporter csvImporter;
    @Autowired
    private ShoeService shoeService;

    // Liste von Schuhen einfügen
    @GetMapping("/insert")
    public String insertShoes(Model model) {
       if( shoeService.getAllShoes().size()<=0) {
           List<Shoe> shoes = csvImporter.importCsv("src/main/resources/Schuhe.csv");
           shoeService.saveAllShoes(shoes);
           model.addAttribute("shoe", shoes);
       }else{
           model.addAttribute("shoe",shoeService.getAllShoes());
       }
        return "shoe";

    }
    @GetMapping("/list")
    public String getList( Model model) {
        List<Shoe> shoes=shoeService.getAllShoes();
        double totalValueBought = shoes.stream().mapToDouble(Shoe::getValueBought).sum();
        double totalValueStockX = shoes.stream().mapToDouble(Shoe::getValueStockX).sum();
        double totalWinStockX = totalValueStockX - totalValueBought;

        // Werte formatieren
        String formattedTotalValueBought = String.format("%.2f €", totalValueBought);
        String formattedTotalValueStockX = String.format("%.2f €", totalValueStockX);
        String formattedTotalWinStockX = String.format("%.2f € (%.2f%%)", totalWinStockX, (totalWinStockX / totalValueBought) * 100);

        model.addAttribute("shoe", shoes);
        model.addAttribute("totalValueBought", formattedTotalValueBought);
        model.addAttribute("totalValueStockX", formattedTotalValueStockX);
        model.addAttribute("totalWinStockX", formattedTotalWinStockX);
        return "shoe";
    }

}
