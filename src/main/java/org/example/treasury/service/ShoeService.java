package org.example.treasury.service;

import java.util.List;
import org.example.treasury.model.Shoe;
import org.example.treasury.repository.ShoeRepository;
import org.springframework.stereotype.Service;

/**
 * ShoeService is a service class that provides methods to interact with the ShoeRepository.
 */

@Service

public class ShoeService {

  /**
   * Updates the sold value of a shoe.
   *
   * @param shoeId    the shoe ID
   * @param valueSold the sold value
   */
  public void updateValueSold(String shoeId, Double valueSold) {
    Shoe shoe = shoeRepository.findById(shoeId)
        .orElseThrow(() -> new IllegalArgumentException("Schuh nicht gefunden: " + shoeId));
    shoe.setValueSold(valueSold);
    shoeRepository.save(shoe);
  }

  /**
   * Updates the Klekt market value of a shoe.
   *
   * @param shoeId     the shoe ID
   * @param klektPrice the new price from Klekt in EUR
   */
  public void updateKlektPrice(String shoeId, Double klektPrice) {
    Shoe shoe = shoeRepository.findById(shoeId)
        .orElseThrow(() -> new IllegalArgumentException("Schuh nicht gefunden: " + shoeId));
    shoe.setValueStockX(klektPrice);
    shoe.setWinStockX(klektPrice - shoe.getValueBought());
    shoeRepository.save(shoe);
  }

  /**
   * Updates the Klekt product slug for a shoe.
   *
   * @param shoeId    the shoe ID
   * @param klektSlug the Klekt product slug (e.g. "yeezy-boost-350-v2-zebra")
   */
  public void updateKlektSlug(String shoeId, String klektSlug) {
    Shoe shoe = shoeRepository.findById(shoeId)
        .orElseThrow(() -> new IllegalArgumentException("Schuh nicht gefunden: " + shoeId));
    shoe.setKlektSlug(klektSlug == null ? null : klektSlug.trim());
    shoeRepository.save(shoe);
  }

  private final ShoeRepository shoeRepository;

  /**
   * Constructor for ShoeService.
   *
   * @param shoeRepository the ShoeRepository instance
   */
  public ShoeService(ShoeRepository shoeRepository) {
    this.shoeRepository = shoeRepository;
  }

  /**
   * Gets all shoes.
   *
   * @return a list of all shoes
   */
  public List<Shoe> getAllShoes() {
    return shoeRepository.findAll();
  }

  /**
   * Save all shoes.
   *
   * @param shoes the list of shoes to save
   */
  public void saveAllShoes(List<Shoe> shoes) {
    shoeRepository.saveAll(shoes);
  }

}