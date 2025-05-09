package org.example.treasury.controller;
import java.util.Map;
import org.example.treasury.model.Display;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.DisplayService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/display")
public class DisplayController {

    private final DisplayService displayService;
    private final  CsvImporter csvImporter;

    public DisplayController( CsvImporter csvImporter, DisplayService displayService) {
        this.csvImporter = csvImporter;
        this.displayService = displayService;
    }
    // Liste von Schuhen einfügen
    @GetMapping("/insert")
    public String insertDisplays(Model model) {
        List<Display> displays=displayService.getAllDisplays();
        if(displays.isEmpty()) {
            displays = csvImporter.importDisplayCsv("src/main/resources/Displays.csv");

            model.addAttribute("displays", displays);
            displayService.saveAllDisplays(displays);
        }else{
            model.addAttribute("displays",displays);
        }
        return "display";

    }
    @GetMapping("/aggregated")
    public String getAggregatedValues(Model model) {
        Map<String,
                Map<String,
                        Map<String, Object>>> aggregatedValues = displayService.getAggregatedValues();
        model.addAttribute("aggregatedValues", aggregatedValues);
        System.out.println(aggregatedValues);
        return "aggregated-display";
    }
    @GetMapping
    public List<Display> getAllDisplays() {
        return displayService.getAllDisplays();
    }

    @GetMapping("/{id}")
    public Display getDisplayById(@PathVariable String id) {
        return displayService.getDisplayById(id);
    }

    @PostMapping
    public Display createDisplay(@RequestBody Display display) {
        return displayService.saveDisplay(display);
    }

    @PutMapping("/{id}")
    public Display updateDisplay(@PathVariable String id, @RequestBody Display display) {
        display.set_id(id);
        return displayService.saveDisplay(display);
    }

    @DeleteMapping("/{id}")
    public void deleteDisplay(@PathVariable String id) {
        displayService.deleteDisplay(id);
    }

    @GetMapping("/setCode/{setCode}")
    public List<Display> getDisplaysBySetCode(@PathVariable String setCode) {
        return displayService.findBySetCodeIgnoreCase(setCode);
    }

    @GetMapping("/type/{type}")
    public List<Display> getDisplaysByType(@PathVariable String type) {
        return displayService.getDisplaysByType(type);
    }

    @GetMapping("/valueRange")
    public List<Display> getDisplaysByValueRange(@RequestParam double minValue, @RequestParam double maxValue) {
        return displayService.getDisplaysByValueRange(minValue, maxValue);
    }


    @GetMapping("/list")
    public String getList(@RequestParam(value = "setCode", required = false) String setCode, Model model) {


        List<Display> displays;
        if (setCode != null && !setCode.isEmpty()) {
            displays = displayService.findBySetCodeIgnoreCase(setCode);
        } else {
            displays = displayService.getAllDisplays();
        }
        //Comment
        model.addAttribute("displays", displays);
        return "display";
    }

}