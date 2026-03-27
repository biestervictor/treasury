package org.example.treasury.model;

/**
 * Stabile Schlüssel für Jobs. Diese Keys sind bewusst unabhängig von Bean-/Class-Namen,
 * damit Settings später persistiert werden können.
 */
public enum JobKey {
  SELL,
  PRICE_SCRAPER,
  MAGIC_SET,
  METAL_PRICE_SCRAPER
}

