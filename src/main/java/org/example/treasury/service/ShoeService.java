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
   * Updates the Klekt Ask price (lowest listing) and Bid price (highest buy-order) for a shoe.
   *
   * @param shoeId   the shoe ID
   * @param ask      the lowest Ask price from Klekt in EUR
   * @param bid      the highest Bid price from Klekt in EUR (0 if unavailable)
   */
  public void updateKlektPrices(String shoeId, double ask, double bid) {
    Shoe shoe = shoeRepository.findById(shoeId)
        .orElseThrow(() -> new IllegalArgumentException("Schuh nicht gefunden: " + shoeId));
    shoe.setValueStockX(ask);
    shoe.setWinStockX(ask - shoe.getValueBought());
    shoe.setKlektBid(bid);
    shoeRepository.save(shoe);
  }

  /**
   * Updates the Klekt market value of a shoe.
   *
   * @param shoeId     the shoe ID
   * @param klektPrice the new price from Klekt in EUR
   */
  public void updateKlektPrice(String shoeId, Double klektPrice) {
    updateKlektPrices(shoeId, klektPrice, 0.0);
  }

  /**
   * Updates the StockX Ask and Bid prices for a shoe.
   *
   * @param shoeId   the shoe ID
   * @param ask      the lowest Ask price from StockX in EUR (0 if unavailable)
   * @param bid      the highest Bid price from StockX in EUR (0 if unavailable)
   */
  public void updateStockxPrices(String shoeId, double ask, double bid) {
    Shoe shoe = shoeRepository.findById(shoeId)
        .orElseThrow(() -> new IllegalArgumentException("Schuh nicht gefunden: " + shoeId));
    shoe.setStockxAsk(ask);
    shoe.setStockxBid(bid);
    shoeRepository.save(shoe);
  }

  /**
   * Updates the StockX product slug for a shoe.
   *
   * @param shoeId     the shoe ID
   * @param stockxSlug the StockX product slug (e.g. "adidas-yeezy-boost-350-v2-zebra")
   */
  public void updateStockxSlug(String shoeId, String stockxSlug) {
    Shoe shoe = shoeRepository.findById(shoeId)
        .orElseThrow(() -> new IllegalArgumentException("Schuh nicht gefunden: " + shoeId));
    shoe.setStockxSlug(stockxSlug == null ? null : stockxSlug.trim());
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