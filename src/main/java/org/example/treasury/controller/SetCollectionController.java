package org.example.treasury.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.treasury.dto.PriceSnapshotDto;
import org.example.treasury.model.MagicSet;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.MtgStocksService;
import org.example.treasury.service.SetCollectionService;
import org.example.treasury.service.WishPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for the set collection overview.
 */
@Controller
@RequestMapping("/api/sets")
public class SetCollectionController {

  private static final Logger logger = LoggerFactory.getLogger(SetCollectionController.class);

  private final MagicSetService magicSetService;
  private final SetCollectionService setCollectionService;
  private final DisplayService displayService;
  private final MtgStocksService mtgStocksService;
  private final WishPriceService wishPriceService;

  /**
   * Constructor.
   *
   * @param magicSetService      the magic set service
   * @param setCollectionService the set collection service
   * @param displayService       the display service
   * @param mtgStocksService     the MTGStocks service
   * @param wishPriceService     the wish price service
   */
  public SetCollectionController(MagicSetService magicSetService,
                                 SetCollectionService setCollectionService,
                                 DisplayService displayService,
                                 MtgStocksService mtgStocksService,
                                 WishPriceService wishPriceService) {
    this.setCollectionService = setCollectionService;
    this.magicSetService = magicSetService;
    this.displayService = displayService;
    this.mtgStocksService = mtgStocksService;
    this.wishPriceService = wishPriceService;
  }

  private static final String DEFAULT_FILTER =
      "funny,core,masters,commander,draft_innovation";

  /**
   * Shows all sets with collection status, hiding the default set types by default.
   *
   * @param model the Spring MVC model
   * @return view name
   */
  @GetMapping("/list")
  public String getSets(Model model) {
    List<String> filters = Arrays.stream(DEFAULT_FILTER.split(",")).toList();
    List<MagicSet> missingSets = setCollectionService.getMissingSets(filters);
    List<MagicSet> allSets = magicSetService.getAllMagicSets().stream()
        .filter(set ->
            !set.getName().equalsIgnoreCase("Time Spiral Timeshifted")
                && !set.getName().equalsIgnoreCase("The Big Score")
                && filters.stream()
                .noneMatch(f -> set.getSetType().equalsIgnoreCase(f)))
        .toList();
    model.addAttribute("missingSets", missingSets);
    model.addAttribute("allSets", allSets);
    model.addAttribute("setType", DEFAULT_FILTER);
    model.addAttribute("setCodeToTypes", buildSetCodeToTypes());
    model.addAttribute("wishPricesMap", buildWishPricesMap(allSets));
    return "setCollection";
  }

  /**
   * Filters sets by type.
   *
   * @param setType comma-separated list of set types to exclude
   * @param model   the Spring MVC model
   * @return view name
   */
  @GetMapping("/filter")
  public String filter(@RequestParam(required = false) String setType, Model model) {
    List<MagicSet> missingSets =
        setCollectionService.getMissingSets(Arrays.stream(setType.split(",")).toList());
    List<MagicSet> allSets = magicSetService.getAllMagicSets().stream()
        .filter(set ->
            !set.getName().equalsIgnoreCase("Time Spiral Timeshifted")
                && !set.getName().equalsIgnoreCase("The Big Score")
                && Arrays.stream(setType.split(",")).toList().stream()
                .noneMatch(filter -> set.getSetType().equalsIgnoreCase(filter)))
        .toList();

    model.addAttribute("missingSets", missingSets);
    model.addAttribute("setType", setType);
    model.addAttribute("allSets", allSets);
    model.addAttribute("setCodeToTypes", buildSetCodeToTypes());
    model.addAttribute("wishPricesMap", buildWishPricesMap(allSets));
    return "setCollection";
  }

  /**
   * Builds a map from setCode to the list of distinct display types present in the inventory.
   *
   * @return map of setCode to list of type strings
   */
  private Map<String, List<String>> buildSetCodeToTypes() {
    return displayService.getAllDisplays().stream()
        .filter(d -> !d.isSold())
        .collect(Collectors.groupingBy(
            d -> d.getSetCode().toUpperCase(),
            Collectors.mapping(d -> d.getType().toUpperCase(),
                Collectors.collectingAndThen(Collectors.toList(),
                    list -> list.stream().distinct().sorted().toList()))));
  }

  /**
   * Builds a map from setCode (uppercase) to its {@code wishPrices} map,
   * including only sets that have at least one wish price configured.
   *
   * @param sets the list of MagicSets to inspect
   * @return map of setCode to wish-price map (type → price)
   */
  private Map<String, Map<String, Double>> buildWishPricesMap(List<MagicSet> sets) {
    return sets.stream()
        .filter(s -> s.getWishPrices() != null && !s.getWishPrices().isEmpty())
        .collect(Collectors.toMap(
            s -> s.getCode().toUpperCase(),
            MagicSet::getWishPrices));
  }

  /**
   * Sets or removes the wish price for a given set code and display type.
   * A price of {@code 0} removes the entry for that type.
   *
   * @param code  the set code (case-insensitive)
   * @param type  the display type (e.g. DRAFT, COLLECTOR)
   * @param price the desired maximum price in EUR; {@code 0} to remove
   * @return 200 OK
   */
  @PostMapping("/{code}/wishPrice")
  @ResponseBody
  public ResponseEntity<Void> setWishPrice(@PathVariable String code,
                                           @RequestParam String type,
                                           @RequestParam double price) {
    wishPriceService.setWishPrice(code.toUpperCase(), type.toUpperCase(), price);
    return ResponseEntity.ok().build();
  }

  /**
   * Removes the wish price entry for a given set code and display type.
   *
   * @param code the set code (case-insensitive)
   * @param type the display type to remove
   * @return 200 OK
   */
  @DeleteMapping("/{code}/wishPrice")
  @ResponseBody
  public ResponseEntity<Void> removeWishPrice(@PathVariable String code,
                                              @RequestParam String type) {
    wishPriceService.removeWishPrice(code.toUpperCase(), type.toUpperCase());
    return ResponseEntity.ok().build();
  }

  /**
   * Returns the Cardmarket price history for a set and display type as a JSON array for Chart.js.
   *
   * @param code the set code (case-insensitive)
   * @param type the display type (e.g. DRAFT, COLLECTOR)
   * @return list of date/price data points
   */
  @GetMapping("/{code}/{type}/priceHistory")
  @ResponseBody
  public List<PriceSnapshotDto> getPriceHistory(@PathVariable String code,
                                                @PathVariable String type) {
    return wishPriceService.getPriceHistory(code.toUpperCase(), type.toUpperCase()).stream()
        .map(s -> new PriceSnapshotDto(s.getDate().toString(), s.getPrice()))
        .toList();
  }

  /**
   * Fetches booster box image URLs from MTGStocks and persists them on all MagicSets.
   *
   * @return redirect to the set list
   */
  @PostMapping("/updateBoosterImages")
  public String updateBoosterImages() {
    try {
      Map<String, String> imageUrls = mtgStocksService.fetchBoosterBoxImageUrls();
      magicSetService.updateBoosterBoxImages(imageUrls);
    } catch (Exception e) {
      logger.warn("Booster-Box-Bilder konnten nicht aktualisiert werden: {}", e.getMessage());
    }
    return "redirect:/api/sets/list";
  }
}
