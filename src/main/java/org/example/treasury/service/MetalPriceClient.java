package org.example.treasury.service;

import java.time.Instant;

/**
 * Abstraktion für den Abruf aktueller Edelmetallpreise.
 *
 * <p>Für Tests wird dieses Interface gemockt.</p>
 */
public interface MetalPriceClient {

  record Prices(double goldEurPerOunce, double silverEurPerOunce, Instant timestamp) {
  }

  Prices fetchCurrentPrices();
}

