package org.example.treasury.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
import org.example.treasury.repository.DisplayRepository;
import org.springframework.stereotype.Service;

/**
 * DisplayService is a service class that provides methods to interact with the DisplayRepository.
 */

@Service

public class DisplayService {

  private final DisplayRepository displayRepository;


  /**
   * Constructor for DisplayService.
   *
   * @param displayRepository the DisplayRepository instance
   */
  public DisplayService(DisplayRepository displayRepository, MagicSetService magicSetService) {
    this.displayRepository = displayRepository;

  }

  /**
   * Get all displays.
   *
   * @return a list of all displays
   */
  public List<Display> getAllDisplays() {
    return displayRepository.findAll();
  }

  /**
   * Get a display by its ID.
   *
   * @param id the ID of the display
   * @return the display with the specified ID, or null if not found
   */
  public Display getDisplayById(String id) {
    return displayRepository.findById(id).orElse(null);
  }

  /**
   * Save a display.
   *
   * @param display the display to save
   * @return the saved display
   */
  public Display saveDisplay(Display display) {
    return displayRepository.save(display);
  }

  /**
   * Delete a display by its ID.
   *
   * @param id display ID
   */
  public void deleteDisplay(String id) {
    displayRepository.deleteById(id);
  }

  /**
   * Find displays by set code, ignoring case.
   *
   * @param setCode the set code to search for
   * @return a list of displays with the specified set code
   */
  public List<Display> findBySetCodeIgnoreCase(String setCode) {
    return displayRepository.findBySetCodeIgnoreCase(setCode);
  }

  /**
   * Find displays by type.
   *
   * @param type the type to search for
   * @return a list of displays with the specified type
   */
  public List<Display> getDisplaysByType(String type) {
    return displayRepository.findByType(type);
  }

  /**
   * Find displays by value range.
   *
   * @param minValue the minimum value
   * @param maxValue the maximum value
   * @return a list of displays with values within the specified range
   */
  public List<Display> getDisplaysByValueRange(double minValue, double maxValue) {
    return displayRepository.findByValueBoughtBetween(minValue, maxValue);
  }


  /**
   * Save all displays.
   *
   * @param displays the list of displays to save
   */
  public void saveAllDisplays(List<Display> displays) {
    displayRepository.saveAll(displays);
  }

  /**
   * Get aggregated values of displays.
   *
   * @return a map containing aggregated values of displays
   */
  public Map<String, Map<String, Map<String, Object>>> getAggregatedValues() {
    List<Display> displays = getAllDisplays(); // Holt alle Displays aus der Datenbank
    return displays.stream()
        .collect(Collectors.groupingBy(
            Display::getSetCode,
            Collectors.groupingBy(
                Display::getType,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                      double totalValue = list.stream().mapToDouble(Display::getValueBought).sum();
                      long count = list.size();
                      double avgRelevantPreis = list.stream()
                          .map(this::getRelevantPreis)
                          .filter(Objects::nonNull)
                          .mapToDouble(Double::doubleValue)
                          .average()
                          .orElse(0.0);
                      double averagePrice = count > 0 ? totalValue / count : 0;
                      Map<String, Object> result = new HashMap<>();
                      result.put("totalValue", totalValue);
                      result.put("count", count);
                      result.put("averagePrice", averagePrice);
                      result.put("relevantPreis", avgRelevantPreis);
                      return result;
                    }
                )
            )
        ));
  }

  private Double getRelevantPreis(Display display) {
    String setCode = display.getSetCode();
    String type = display.getType();
    // Filtere alle Displays mit gleichem setCode und type
    List<Display> gleicheDisplays = getAllDisplays().stream()
        .filter(d -> setCode.equals(d.getSetCode()) && type.equals(d.getType()))
        .collect(Collectors.toList());
    // Sammle alle Angebote dieser Displays
    List<Double> preise = gleicheDisplays.stream()
        .flatMap(d -> d.getAngebotList() != null ? d.getAngebotList().stream() : Stream.empty())
        .map(Angebot::getPreis)
        .filter(Objects::nonNull)
        .sorted()
        .collect(Collectors.toList());
    if (preise.isEmpty()) {
      return null;
    }
    if (preise.size() == 1) {
      return preise.get(0);
    }
    double lowest = preise.get(0);
    double second = preise.get(1);
    if (lowest < second * 0.85) {
      return second;
    } else {
      return lowest;
    }
  }

  public void updateAngeboteBySetCodeAndType(String setCode, String type,
                                             List<Angebot> neueAngebote, String url) {
    List<Display> displays = displayRepository.findBySetCodeIgnoreCase(setCode).stream()
        .filter(display -> type.equals(display.getType()))
        .collect(Collectors.toList());
    for (Display display : displays) {
      display.setUrl(url);
      display.setAngebotList(neueAngebote);
      display.setUpdatedAt(LocalDate.now());
    }
    displayRepository.saveAll(displays);
  }

}