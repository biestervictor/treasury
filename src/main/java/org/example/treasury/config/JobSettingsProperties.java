package org.example.treasury.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime-Settings zum (De-)Aktivieren der Jobs.
 *
 * <p>Diese Werte sollen ber die UI bersteuerbar sein und sind deshalb keine @Value-Felder
 * direkt in den Jobs.</p>
 */
@ConfigurationProperties(prefix = "treasury.jobs")
public class JobSettingsProperties {

  private boolean sellEnabled = true;
  private boolean priceScraperEnabled = true;
  private boolean magicSetEnabled = true;
  private boolean metalPriceScraperEnabled = true;
  private boolean wishPriceCheckerEnabled = false;

  public boolean isSellEnabled() {
    return sellEnabled;
  }

  public void setSellEnabled(boolean sellEnabled) {
    this.sellEnabled = sellEnabled;
  }

  public boolean isPriceScraperEnabled() {
    return priceScraperEnabled;
  }

  public void setPriceScraperEnabled(boolean priceScraperEnabled) {
    this.priceScraperEnabled = priceScraperEnabled;
  }

  public boolean isMagicSetEnabled() {
    return magicSetEnabled;
  }

  public void setMagicSetEnabled(boolean magicSetEnabled) {
    this.magicSetEnabled = magicSetEnabled;
  }

  public boolean isMetalPriceScraperEnabled() {
    return metalPriceScraperEnabled;
  }

  public void setMetalPriceScraperEnabled(boolean metalPriceScraperEnabled) {
    this.metalPriceScraperEnabled = metalPriceScraperEnabled;
  }

  /**
   * Returns whether the WishPriceCheckerJob is enabled.
   *
   * @return true if enabled
   */
  public boolean isWishPriceCheckerEnabled() {
    return wishPriceCheckerEnabled;
  }

  /**
   * Sets the enabled flag for the WishPriceCheckerJob.
   *
   * @param wishPriceCheckerEnabled true to enable
   */
  public void setWishPriceCheckerEnabled(boolean wishPriceCheckerEnabled) {
    this.wishPriceCheckerEnabled = wishPriceCheckerEnabled;
  }
}
