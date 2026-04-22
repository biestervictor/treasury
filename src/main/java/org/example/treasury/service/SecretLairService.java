package org.example.treasury.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.treasury.model.AggregatedSecretLair;
import org.example.treasury.model.SecretLair;
import org.example.treasury.repository.SecretLairRepository;
import org.springframework.stereotype.Service;

/**
 * Service for Secret Lair operations.
 */
@Service
public class SecretLairService {

  private final SecretLairRepository secretLairRepository;

  /**
   * Constructor.
   *
   * @param secretLairRepository the repository
   */
  public SecretLairService(SecretLairRepository secretLairRepository) {
    this.secretLairRepository = secretLairRepository;
  }

  /**
   * Add a single Secret Lair.
   *
   * @param secretLair the item to add
   */
  public void addSecretLair(SecretLair secretLair) {
    secretLairRepository.save(secretLair);
  }

  /**
   * Save all secretlairs.
   *
   * @param secretLairs the list of SecretLairs to save
   */
  public void saveAllSecretLairs(List<SecretLair> secretLairs) {
    secretLairRepository.saveAll(secretLairs);
  }

  /**
   * Update a Secret Lair by ID (without changing the selling flag).
   *
   * @param id           the document ID
   * @param location     new location
   * @param currentValue new current value
   * @param isSold       whether it is sold
   * @param soldPrice    the sold price
   * @param boughtDate   the date it was bought
   */
  public void updateSecretLair(String id, String location, Double currentValue, boolean isSold,
                               Double soldPrice, LocalDate boughtDate) {
    SecretLair sl = secretLairRepository.findById(id).orElseThrow();
    updateSecretLair(id, location, currentValue, isSold, sl.isSelling(), soldPrice, boughtDate);
  }

  /**
   * Update a Secret Lair by ID.
   *
   * @param id           the document ID
   * @param location     new location
   * @param currentValue new current value
   * @param isSold       whether it is sold
   * @param selling      whether it is being sold
   * @param soldPrice    the sold price
   * @param boughtDate   the date it was bought
   */
  public void updateSecretLair(String id, String location, Double currentValue, boolean isSold,
                               boolean selling, Double soldPrice, LocalDate boughtDate) {
    SecretLair sl = secretLairRepository.findById(id).orElseThrow();
    sl.setLocation(location);
    sl.setCurrentValue(currentValue);
    sl.setSold(isSold);
    sl.setSelling(selling);
    sl.setSoldPrice(soldPrice != null ? soldPrice : 0.0);
    sl.setDateBought(boughtDate);
    secretLairRepository.save(sl);
  }

  /**
   * Get all secretlair displays.
   *
   * @return a list of all SecretLair displays
   */
  public List<SecretLair> getAllSecretLairs() {
    return secretLairRepository.findAll();
  }

  /**
   * Finds a SecretLair by its MongoDB document ID.
   *
   * @param id the document ID
   * @return Optional containing the SecretLair if found
   */
  public Optional<SecretLair> findById(String id) {
    return secretLairRepository.findById(id);
  }

  /**
   * Returns all non-sold SecretLairs with the given name.
   *
   * @param name the product name
   * @return list of matching active Secret Lairs
   */
  public List<SecretLair> findActiveByName(String name) {
    return secretLairRepository.findByNameAndIsSoldFalse(name);
  }

  /**
   * Persists a single Secret Lair (update).
   *
   * @param secretLair the item to save
   */
  public void updateSecretLair(SecretLair secretLair) {
    secretLairRepository.save(secretLair);
  }

  /**
   * Returns all non-sold Secret Lairs grouped by name as aggregated view.
   * Within each group the averagePrice is the avg of valueBought,
   * and currentValue is taken from the first entry that has a non-zero value.
   *
   * @return sorted list of AggregatedSecretLair
   */
  public List<AggregatedSecretLair> getAggregatedSecretLairs() {
    List<SecretLair> active = secretLairRepository.findAll().stream()
        .filter(sl -> !sl.isSold())
        .toList();

    Map<String, List<SecretLair>> byName = active.stream()
        .collect(Collectors.groupingBy(SecretLair::getName));

    return byName.entrySet().stream()
        .map(entry -> {
          List<SecretLair> group = entry.getValue();
          AggregatedSecretLair agg = new AggregatedSecretLair();
          agg.setName(entry.getKey());
          agg.setCount(group.size());
          agg.setAveragePrice(group.stream()
              .mapToDouble(SecretLair::getValueBought)
              .average()
              .orElse(0.0));
          agg.setCurrentValue(group.stream()
              .filter(sl -> sl.getCurrentValue() > 0)
              .mapToDouble(SecretLair::getCurrentValue)
              .findFirst()
              .orElse(0.0));
          group.stream()
              .map(SecretLair::getImageUrl)
              .filter(url -> url != null && !url.isBlank())
              .findFirst()
              .ifPresent(agg::setImageUrl);
          return agg;
        })
        .sorted(Comparator.comparing(AggregatedSecretLair::getName))
        .toList();
  }
}
