package org.example.treasury.service;

import java.util.List;
import org.example.treasury.model.Display;
import org.example.treasury.model.MagicSet;
import org.springframework.stereotype.Service;

@Service
public class SetCollectionService {
  private final MagicSetService magicSetService;
  private final DisplayService displayService;
    public SetCollectionService(MagicSetService magicSetService, DisplayService displayService) {
        this.magicSetService = magicSetService;
        this.displayService = displayService;
    }

    public List<MagicSet> getMissingSets() {
        List<MagicSet> allSets = magicSetService.getAllMagicSets();
        List<Display> allDisplays = displayService.getAllDisplays();

        // Filter sets that are not present in displays
    List<MagicSet> missingSets = allSets.stream()
                    .filter(set -> allDisplays.stream()
                        .noneMatch(display -> display.getSetCode().equals(set.getCode())) &&
                        !set.getName().equalsIgnoreCase("Time Spiral Timeshifted") &&
                        !set.getName().equalsIgnoreCase("The Big Score") &&
                        !set.getSetType().equalsIgnoreCase("funny") &&
                        !set.getSetType().equalsIgnoreCase("core") &&
                        !set.getSetType().equalsIgnoreCase("draft_innovation")&&
                        !set.getSetType().equalsIgnoreCase("masters"))
                    .toList();
        return missingSets;
    }

  public List<MagicSet> getMissingSets(List<String> setFilter) {
    List<MagicSet> allSets = magicSetService.getAllMagicSets();
    List<Display> allDisplays = displayService.getAllDisplays();

    // Filter sets that are not present in displays
    List<MagicSet> missingSets = allSets.stream()
        .filter(set -> allDisplays.stream()
            .noneMatch(display -> display.getSetCode().equals(set.getCode())) &&
            !set.getName().equalsIgnoreCase("Time Spiral Timeshifted") &&
            !set.getName().equalsIgnoreCase("The Big Score") &&
            !setFilter.stream().anyMatch(filter -> set.getSetType().equalsIgnoreCase(filter)))
        .toList();
    return missingSets;
  }

}
