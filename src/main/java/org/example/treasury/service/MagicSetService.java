package org.example.treasury.service;

import java.util.List;
import org.example.treasury.model.MagicSet;
import org.example.treasury.repository.MagicSetRepository;
import org.springframework.stereotype.Service;

/**
 * MagicSetService is a service class that provides methods to interact with the MagicSetRepository.
 */

@Service

public class MagicSetService {

  private final MagicSetRepository magicSetRepository;

  /**
   * Constructor for MagicSetService.
   *
   * @param magicSetRepository the MagicSetRepository instance
   */
  public MagicSetService(MagicSetRepository magicSetRepository) {
    this.magicSetRepository = magicSetRepository;
  }

  /**
   * Get all MagicSets.
   *
   * @return a list of all MagicSets
   */
  public List<MagicSet> getAllMagicSets() {
    return magicSetRepository.findAll();
  }

  /**
   * Get a MagicSet by its ID.
   *
   * @param code the setCode of the MagicSet
   * @return the MagicSet with the specified ID, or null if not found
   */
  public List<MagicSet> getMagicSetByCode(String code) {
    return magicSetRepository.findAllByCode(code);
  }

  /**
   * Save a MagicSet.
   *
   * @param magicSet the MagicSet to save
   * @return the saved MagicSet
   */
  public MagicSet saveMagicSet(MagicSet magicSet) {
    return magicSetRepository.save(magicSet);
  }

  /**
   * save a list of MagicSets.
   *
   * @param magicSets set of MagicSets to save
   * @return the saved MagicSets
   */
  public List<MagicSet> saveAllMagicSets(List<MagicSet> magicSets) {
    return magicSetRepository.saveAll(magicSets);
  }


}