package org.example.treasury.service;

import java.util.List;
import org.example.treasury.model.Display;
import org.example.treasury.model.MagicSet;
import org.springframework.stereotype.Service;

/**
 * Service to determine which Magic sets are missing from the inventory.
 * A set is considered present only if at least one non-sold display exists for it.
 */
@Service
public class SetCollectionService {

  private final MagicSetService magicSetService;
  private final DisplayService displayService;

  /**
   * Constructor for SetCollectionService.
   *
   * @param magicSetService the magic set service
   * @param displayService  the display service
   */
  public SetCollectionService(MagicSetService magicSetService, DisplayService displayService) {
    this.magicSetService = magicSetService;
    this.displayService = displayService;
  }

  /**
   * Returns all sets that have no non-sold display in the inventory,
   * excluding hard-coded sets and the provided type filter list.
   *
   * @param setFilter list of set types to exclude from the result
   * @return list of missing MagicSets
   */
  public List<MagicSet> getMissingSets(List<String> setFilter) {
    List<MagicSet> allSets = magicSetService.getAllMagicSets();
    List<Display> unsoldDisplays = displayService.getAllDisplays().stream()
        .filter(d -> !d.isSold())
        .toList();

    return allSets.stream()
        .filter(set ->
            unsoldDisplays.stream()
                .noneMatch(display -> display.getSetCode().equals(set.getCode()))
                && !set.getName().equalsIgnoreCase("Time Spiral Timeshifted")
                && !set.getName().equalsIgnoreCase("The Big Score")
                && setFilter.stream()
                .noneMatch(filter -> set.getSetType().equalsIgnoreCase(filter)))
        .toList();
  }
}
