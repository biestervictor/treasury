package org.example.treasury.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.example.treasury.model.Angebot;
import org.example.treasury.model.Display;
import org.example.treasury.model.SecretLair;
import org.example.treasury.repository.SecretLairRepository;
import org.springframework.stereotype.Service;

@Service
public class SecretLairService {

  private final SecretLairRepository secretLairRepository;

  public SecretLairService(SecretLairRepository secretLairRepository) {
    this.secretLairRepository = secretLairRepository;
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
   * Get all secretlair displays.
   * @return a list of all SecretLair displays
   */
  public List<SecretLair> getAllSecretLairs() {
    return secretLairRepository.findAll();
  }

  // In SecretLairService.java
  public void updateSecretLair(SecretLair secretLair) {

      secretLairRepository.save(secretLair);
      }

}