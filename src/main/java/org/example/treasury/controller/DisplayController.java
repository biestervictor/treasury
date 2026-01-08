package org.example.treasury.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.example.treasury.model.AggregatedDisplay;
import org.example.treasury.model.Display;
import org.example.treasury.model.DisplayType;
import org.example.treasury.model.MagicSet;
import org.example.treasury.service.CsvImporter;
import org.example.treasury.service.DisplayPriceCollectorService;
import org.example.treasury.service.PriceCollectorService;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Display Controller.
 */

@Controller
@RequestMapping("/api/display")

public class DisplayController {

  private final DisplayService displayService;
  private final CsvImporter csvImporter;
  private final MagicSetService magicSetService;
  private final List<MagicSet> magicSets;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  private final DisplayPriceCollectorService displayPriceCollectorService;

  /**
   * Constructor for DisplayController.
   *
   * @param csvImporter    csvImporter
   * @param displayService displayService
   */
  public DisplayController(CsvImporter csvImporter, DisplayService displayService,
                           MagicSetService magicSetService,
                           DisplayPriceCollectorService displayPriceCollectorService) {
    this.csvImporter = csvImporter;
    this.displayService = displayService;

    this.magicSetService = magicSetService;
    magicSets = magicSetService.getAllMagicSets();
    this.displayPriceCollectorService = displayPriceCollectorService;
  }

  /**
   * Form to add Display.
   *
   * @param model the model to add attributes to
   * @return the name of the view to render
   * @throws Exception
   */
  @GetMapping("/new")
  public String addDisplay(Model model) throws Exception {
    try {
      List<MagicSet> magicSets = magicSetService.getAllMagicSets();
      model.addAttribute("magicSets", magicSets);
    } catch (Exception e) {
      logger.error("Hinzufügen fehlgeschlagen", e);
    }

    model.addAttribute("types", Arrays.stream(DisplayType.values()).toList());
    model.addAttribute("display", new Display());
    return "addDisplay";
  }

  /**
   * Save Display.
   *
   * @param display the display to save
   * @return redirect to the display list
   */
  @PostMapping("/save")
  public String saveDisplay(@ModelAttribute Display display) {
    displayService.saveDisplay(display);
    return "redirect:/api/display/list";
  }

  /**
   * Insert displays from CSV file.
   *
   * @param model für das frontend
   * @return response
   */
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
    Map<String, String> setCodeToIconUri = magicSets.stream().distinct().collect(
        Collectors.toMap(MagicSet::getCode, MagicSet::getIconUri));
    model.addAttribute("setCodeToIconUri", setCodeToIconUri);
    model.addAttribute("display", new Display());
    return "display";

  }

  /**
   * Get aggregated displays.
   *
   * @param model für das frontend
   * @return aggregated displays
   * @throws Exception exception
   */
  @GetMapping("/aggregated")
  public String getAggregatedDisplays(Model model) throws Exception {
    Map<String, Map<String, Map<String, Object>>> aggregatedValues =
        displayService.getAggregatedValues();
    List<AggregatedDisplay> aggregatedData = new ArrayList<>();
    aggregatedValues.forEach((setCode, typeMap) -> typeMap.forEach((type, data) -> {
      AggregatedDisplay entry = new AggregatedDisplay();
      entry.setSetCode(setCode);
      entry.setType(type);
      entry.setCount((Long) data.get("count"));
      entry.setAveragePrice((Double) data.get("averagePrice"));
      entry.setSanitizedMarketPrice((Double) data.get("relevantPreis"));
      magicSets.stream()
          .filter(magicSet -> magicSet.getCode().equals(setCode))
          .findFirst()
          .ifPresent(magicSet -> entry.setIconUri(magicSet.getIconUri()));

      aggregatedData.add(entry);
    }));

    model.addAttribute("types", Arrays.stream(DisplayType.values()).toList());
    model.addAttribute("magicSets", magicSets);
    aggregatedData.sort(Comparator.comparing(AggregatedDisplay::getSetCode));
    model.addAttribute("aggregatedData", aggregatedData);
    model.addAttribute("display", new Display());

    return "aggregatedDisplays";
  }

  /**
   * Get all displays.
   *
   * @return list of displays
   */
  @GetMapping
  public List<Display> getAllDisplays() {

    return displayService.getAllDisplays();
  }

  /**
   * Get display by ID.
   *
   * @param id the ID of the display
   * @return the display with the specified ID
   */
  @GetMapping("/{id}")
  public Display getDisplayById(@PathVariable String id) {
    return displayService.getDisplayById(id);
  }

  /**
   * Create a new display.
   *
   * @param display the display to create
   * @return the created display
   */
  @PostMapping
  public Display createDisplay(@RequestBody Display display) {
    return displayService.saveDisplay(display);
  }

  /**
   * Update an existing display.
   *
   * @param id      the ID of the display to update
   * @param display the updated display data
   * @return the updated display
   */
  @PutMapping("/{id}")
  public Display updateDisplay(@PathVariable String id, @RequestBody Display display) {
    display.setId(id);
    return displayService.saveDisplay(display);
  }

  /**
   * Delete a display by ID.
   *
   * @param id the ID of the display to delete
   */
  @DeleteMapping("/{id}")
  public void deleteDisplay(@PathVariable String id) {
    displayService.deleteDisplay(id);
  }

  /**
   * Get displays by set code.
   *
   * @param setCode the set code to search for
   * @return a list of displays with the specified set code
   */
  @GetMapping("/setCode/{setCode}")
  public List<Display> getDisplaysBySetCode(@PathVariable String setCode) {
    return displayService.findBySetCodeIgnoreCase(setCode);
  }

  /**
   * Get displays by type.
   *
   * @param type the type of the display
   * @return a list of displays with the specified type
   */
  @GetMapping("/type/{type}")
  public List<Display> getDisplaysByType(@PathVariable String type) {
    return displayService.getDisplaysByType(type);
  }

  /**
   * Get displays by value range.
   *
   * @param minValue the minimum value
   * @param maxValue the maximum value
   * @return a list of displays within the specified value range
   */
  @GetMapping("/valueRange")
  public List<Display> getDisplaysByValueRange(@RequestParam double minValue,
                                               @RequestParam double maxValue) {
    return displayService.getDisplaysByValueRange(minValue, maxValue);
  }

  /**
   * Get all displays and add them to the model.
   *
   * @param setCode the set code to filter by (optional)
   * @param model   the model to add attributes to
   * @return the name of the view
   */
  @GetMapping("/list")
  public String getList(@RequestParam(value = "setCode", required = false) String setCode,
                        @RequestParam(value = "type", required = false) String type,
                        @RequestParam(value = "soldOnly", required = false, defaultValue = "false") String soldOnly,
                        @RequestParam(value = "highProfitOnly", required = false, defaultValue = "false") String highProfitOnly,
                        Model model) {
    List<Display> displays;
    if (setCode != null && !setCode.isEmpty() && type != null && !type.isEmpty()) {
      displays = displayService.findBySetCodeAndType(setCode, type);
    } else if (setCode != null && !setCode.isEmpty()) {
      displays = displayService.findBySetCodeIgnoreCase(setCode);
    } else if (type != null && !type.isEmpty()) {
      displays = displayService.findByTypeIgnoreCase(type);
    } else {
      displays = displayService.getAllDisplays();
    }
    double sumAktuellerPreis = displays.stream()
        .filter(d -> !d.isSold())
        .mapToDouble(Display::getCurrentValue)
        .sum();
    double sumEinkaufspreis = displays.stream()
        .filter(d -> !d.isSold())
        .mapToDouble(Display::getValueBought)
        .sum();

    // Gewinn pro verkauftem Display (Summe)
    double sumGewinn = displays.stream()
        .filter(Display::isSold)
        .mapToDouble(d -> d.getSoldPrice() - d.getValueBought())
        .sum();
    //boolean filterSoldOnly = "true".equalsIgnoreCase(soldOnly);
    model.addAttribute("soldOnly", soldOnly);

    boolean filterHighProfitOnly = "true".equalsIgnoreCase(highProfitOnly);
    model.addAttribute("highProfitOnly", filterHighProfitOnly);


      displays = displays.stream().filter(d -> d.isSold()==Boolean.parseBoolean(soldOnly)).toList();


    if (filterHighProfitOnly) {
      displays = displays.stream()
          .filter(d -> d.getCurrentValue() > d.getValueBought() * 1.5)
          .toList();
    }

    Map<String, String> setCodeToIconUri = magicSets.stream().distinct().collect(
        Collectors.toMap(MagicSet::getCode, MagicSet::getIconUri));


    model.addAttribute("sumGewinn", sumGewinn);

    model.addAttribute("sumEinkaufspreis", sumEinkaufspreis);
    model.addAttribute("sumAktuellerPreis", sumAktuellerPreis);
    model.addAttribute("setCodeToIconUri", setCodeToIconUri);
    model.addAttribute("types", Arrays.stream(DisplayType.values()).toList());
    model.addAttribute("magicSets", magicSets);
    model.addAttribute("display", new Display());
    model.addAttribute("displays", displays);
    return "display";
  }
  @PostMapping("/update")
  public String updateDisplay(@ModelAttribute Display display) {
    displayService.updateDisplayById(display.getId(), display);
    return "redirect:/api/display/list";
  }
}

