package org.example.treasury.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.example.treasury.model.Display;
import org.example.treasury.model.MagicSet;
import org.example.treasury.service.MagicSetService;
import org.example.treasury.service.SetCollectionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RequestMapping("/api/sets")
public class SetCollectionController {
  private final MagicSetService magicSetService;
private final SetCollectionService setCollectionService;
  public SetCollectionController(MagicSetService magicSetService, SetCollectionService setCollectionService) {
    this.setCollectionService = setCollectionService;
    this.magicSetService = magicSetService;
  }

  @GetMapping("/list")
  public String getSets(Model model) {
    // Beispiel: Sets aus Service holen und nach Typ filtern
    List<MagicSet> missingSets = setCollectionService.getMissingSets();
    List<MagicSet> allSets = magicSetService.getAllMagicSets();
   model.addAttribute("missingSets", missingSets);
    model.addAttribute("allSets", allSets);
    return "setCollection";
  }
  @GetMapping("/filter")
  public String filter(@RequestParam(required = false) String setType, Model model) {
    List<MagicSet> missingSets = setCollectionService.getMissingSets(Arrays.stream(setType.split(",")).toList());
    List<MagicSet> allSets = magicSetService.getAllMagicSets().stream().filter(set ->
        !set.getName().equalsIgnoreCase("Time Spiral Timeshifted") &&
        !set.getName().equalsIgnoreCase("The Big Score") &&
        !Arrays.stream(setType.split(",")).toList().stream().anyMatch(filter -> set.getSetType().equalsIgnoreCase(filter))).toList();

    model.addAttribute("missingSets", missingSets);
    model.addAttribute("setType", setType);
    model.addAttribute("allSets", allSets);
    return "setCollection";
  }

  // Service und DTO bitte entsprechend implementieren
}
