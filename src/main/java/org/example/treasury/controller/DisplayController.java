package org.example.treasury.controller;

import org.example.treasury.model.AggregatedDisplay;
import org.example.treasury.model.Display;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.ScryFallWebservice;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/display")
public class DisplayController {

    private final DisplayService displayService;
    private final CsvImporter csvImporter;
    private final ScryFallWebservice scryFallWebservice;

    public DisplayController(CsvImporter csvImporter, DisplayService displayService, ScryFallWebservice scryFallWebservice) {
        this.csvImporter = csvImporter;
        this.displayService = displayService;
        this.scryFallWebservice = scryFallWebservice;
    }

    // Liste von Schuhen einfügen
    @GetMapping("/insert")
    public String insertDisplays(Model model) {
        List<Display> displays = displayService.getAllDisplays();
        if (displays.isEmpty()) {
            displays = csvImporter.importDisplayCsv("src/main/resources/Displays.csv");

            model.addAttribute("displays", displays);
            displayService.saveAllDisplays(displays);
        } else {
            model.addAttribute("displays", displays);
        }
        return "display";

    }


    @GetMapping("/aggregated")
    public String getAggregatedDisplays(Model model) throws Exception {
        Map<String, Map<String, Map<String, Object>>> aggregatedValues = displayService.getAggregatedValues();
        List<AggregatedDisplay> aggregatedData = new ArrayList<>();
        aggregatedValues.forEach((setCode, typeMap) ->
                typeMap.forEach((type, data) -> {
                    AggregatedDisplay entry = new AggregatedDisplay();
                    entry.setSetCode(setCode);
                    entry.setType(type);
                    entry.setCount((Long) data.get("count"));
                    entry.setAveragePrice((Double) data.get("averagePrice"));
                    entry.setIconUri(data.get("iconUri") != null ? (String) data.get("iconUri") : "");
                    aggregatedData.add(entry);
                })
        );
        aggregatedData.sort(Comparator.comparing(AggregatedDisplay::getSetCode));
        model.addAttribute("aggregatedData", aggregatedData);
        scryFallWebservice.getSetList();
        return "aggregatedDisplays";
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