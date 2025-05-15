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
   * get all shoes.
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