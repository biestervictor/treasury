package org.example.treasury.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.treasury.model.MagicSet;
import org.example.treasury.service.DisplayService;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.SetCollectionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the set collection overview.
 */
@Controller
@RequestMapping("/api/sets")
public class SetCollectionController {

  private final MagicSetService magicSetService;
  private final SetCollectionService setCollectionService;
  private final DisplayService displayService;

  /**
   * Constructor.
   *
   * @param magicSetService      the magic set service
   * @param setCollectionService the set collection service
   * @param displayService       the display service
   */
  public SetCollectionController(MagicSetService magicSetService,
                                 SetCollectionService setCollectionService,
                                 DisplayService displayService) {
    this.setCollectionService = setCollectionService;
    this.magicSetService = magicSetService;
    this.displayService = displayService;
  }

  /**
   * Shows all sets with collection status.
   *
   * @param model the Spring MVC model
   * @return view name
   */
  @GetMapping("/list")
  public String getSets(Model model) {
    List<MagicSet> missingSets = setCollectionService.getMissingSets();
    List<MagicSet> allSets = magicSetService.getAllMagicSets();
    model.addAttribute("missingSets", missingSets);
    model.addAttribute("allSets", allSets);
    model.addAttribute("setCodeToTypes", buildSetCodeToTypes());
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
}
