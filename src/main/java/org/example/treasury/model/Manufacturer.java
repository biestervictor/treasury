package org.example.treasury.model;

/**
 * Bekannte Münzhersteller / Prägeanstalten für Edelmetall-Sammlermünzen.
 *
 * <p>Anwendungsbeispiele:
 * <ul>
 *   <li>{@link #PERTH_MINT} – Lunar-Serie, Känguru, Quokka (Australien)</li>
 *   <li>{@link #NEW_ZEALAND_MINT} – Star-Wars-Serie, Niue-Ausgaben</li>
 *   <li>{@link #NIUE} – Emittent diverser NZ-Mint-Prägungen (z.B. Star Wars)</li>
 *   <li>{@link #CHINA_MINT} – Panda-Goldmünzen</li>
 *   <li>{@link #DEGUSSA} – Degussa-Goldbarren</li>
 *   <li>{@link #UMICORE} – Umicore-Edelmetallprodukte</li>
 *   <li>{@link #BAVARIAN_STATE_MINT} – Somalia-Elefant-Serie u. a.</li>
 *   <li>{@link #MUENZE_DEUTSCHLAND} – Deutsche Sammlergoldmünzen (z.B. Ulm)</li>
 * </ul>
 * </p>
 */
public enum Manufacturer {

  /** Perth Mint (Australien) – Lunar-Serie, Känguru, Quokka u. v. m. */
  PERTH_MINT("Perth Mint"),

  /** New Zealand Mint – Niue-Ausgaben (Star Wars, DC Comics etc.). */
  NEW_ZEALAND_MINT("New Zealand Mint"),

  /**
   * Niue – Emittent (Inselstaat) für diverse NZ-Mint-Prägungen.
   * Wird ggf. als eigenständige Marke geführt.
   */
  NIUE("Niue"),

  /** China Gold Coin Corporation / China Mint – Panda-Goldmünzen. */
  CHINA_MINT("China Mint"),

  /** Degussa Goldhandel GmbH – Goldbarren und Scheidegut. */
  DEGUSSA("Degussa"),

  /** Umicore – Edelmetallprodukte und Barren. */
  UMICORE("Umicore"),

  /**
   * Bayerisches Hauptmünzamt / Bayerisches Münzkontor –
   * Vertreiber u. a. der Somalia-Elefant-Serie.
   */
  BAVARIAN_STATE_MINT("Bayerisches Münzkontor"),

  /**
   * Münze Deutschland (MDM) – Deutsche Sammlermünzen,
   * z. B. Ulm-Goldmünzen und weitere Sonderprägungen.
   */
  MUENZE_DEUTSCHLAND("Münze Deutschland"),

  /** Royal Canadian Mint – Maple-Leaf-Prägungen und Sonderserien. */
  ROYAL_CANADIAN_MINT("Royal Canadian Mint"),

  /** Münze Österreich – Wiener Philharmoniker und österreichische Prägungen. */
  MUENZE_OESTERREICH("Münze Österreich"),

  /** South African Mint – Krugerrand-Prägungen. */
  SOUTH_AFRICAN_MINT("South African Mint"),

  /** Scottsdale Mint (USA) – diverse Collector-Silbermünzen. */
  SCOTTSDALE_MINT("Scottsdale Mint");

  private final String displayName;

  Manufacturer(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Gibt den lesbaren Anzeigenamen des Herstellers zurück.
   *
   * @return Anzeigename
   */
  public String getDisplayName() {
    return displayName;
  }
}
