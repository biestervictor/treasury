package org.example.treasury.dto;

/**
 * Request aus der Settings-UI.
 *
 * <p>Wir nutzen einzelne Felder (statt Map Binding), damit HTML-Forms mit Spring MVC
 * zuverlässig binden.</p>
 */
public record JobSettingsUpdateRequest(
    boolean sellEnabled,
    boolean priceScraperEnabled,
    boolean magicSetEnabled,
    boolean metalPriceScraperEnabled
) {
}

