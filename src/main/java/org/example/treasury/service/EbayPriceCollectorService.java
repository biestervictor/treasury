package org.example.treasury.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.example.treasury.model.Shoe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fetches the lowest current eBay listing price for a shoe using the eBay Finding API.
 *
 * <p>Requires a free eBay Developer App-ID, configured via {@code ebay.app.id} in
 * {@code application.properties}. If no App-ID is set, all lookups return empty.
 *
 * <p>Registration: https://developer.ebay.com/ (kostenlos, sofort verfügbar)
 *
 * <p>Flow:
 * <ol>
 *   <li>Build a search query from shoe name + US size.</li>
 *   <li>Call {@code findItemsAdvanced} on eBay-DE (GLOBAL-ID=EBAY-DE) with
 *       price-ascending sort and Fixed-Price filter.</li>
 *   <li>Return the lowest current price in EUR, post-filtering items that
 *       mention the correct size in their title.</li>
 * </ol>
 */
@Service
public class EbayPriceCollectorService {

  private static final Logger logger = LoggerFactory.getLogger(EbayPriceCollectorService.class);

  private static final String FINDING_API =
      "https://svcs.ebay.com/services/search/FindingService/v1";
  private static final String GLOBAL_ID = "EBAY-DE";

  private final String appId;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /**
   * Constructor – injects the eBay App-ID from configuration.
   *
   * @param appId the eBay Developer App-ID (Client ID), may be blank if not configured
   */
  public EbayPriceCollectorService(@Value("${ebay.app.id:}") String appId) {
    this.appId = appId;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Returns whether eBay search is configured (i.e. App-ID is set).
   *
   * @return true if an App-ID is present
   */
  public boolean isConfigured() {
    return appId != null && !appId.isBlank();
  }

  /**
   * Fetches the lowest current eBay listing price in EUR for the given shoe at its US size.
   * Searches eBay Germany with Fixed-Price listings, sorted by price ascending.
   * Post-filters results to only include listings that mention the US size in the title.
   *
   * @param shoe the shoe to search for
   * @return lowest EUR listing price, or empty if not found or not configured
   */
  public Optional<Double> fetchLowestPrice(Shoe shoe) {
    if (!isConfigured()) {
      logger.debug("eBay App-ID nicht konfiguriert – überspringe {}", shoe.getName());
      return Optional.empty();
    }

    String usSize = normalizeUsSize(shoe.getUsSize());
    String query = buildQuery(shoe.getName(), usSize);

    try {
      String url = buildUrl(query);
      String json = get(url);
      return parseLowestPrice(json, usSize);
    } catch (Exception e) {
      logger.error("eBay-Fehler für '{}': {}", shoe.getName(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Builds the eBay Finding API URL for a price-ascending Fixed-Price search on EBAY-DE.
   *
   * @param query the encoded search query
   * @return full URL string
   */
  private String buildUrl(String query) {
    return FINDING_API
        + "?OPERATION-NAME=findItemsAdvanced"
        + "&SERVICE-VERSION=1.13.0"
        + "&SECURITY-APPNAME=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&GLOBAL-ID=" + GLOBAL_ID
        + "&RESPONSE-DATA-FORMAT=JSON"
        + "&keywords=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
        + "&itemFilter(0).name=ListingType&itemFilter(0).value=FixedPrice"
        + "&itemFilter(1).name=Currency&itemFilter(1).value=EUR"
        + "&sortOrder=PricePlusShippingLowest"
        + "&paginationInput.entriesPerPage=10";
  }

  /**
   * Builds a targeted search query from shoe name and size.
   *
   * @param name   shoe name (e.g. "Yeezy Boost 350 V2 Zebra")
   * @param usSize normalized US size string (e.g. "11", "10.5")
   * @return combined search query
   */
  private String buildQuery(String name, String usSize) {
    // US-Größe explizit in Query, damit eBay-Listing-Titel gefiltert werden kann
    if (usSize != null) {
      return name + " US " + usSize;
    }
    return name;
  }

  /**
   * Parses the eBay Finding API JSON response and returns the lowest price
   * whose listing title contains the expected US size.
   *
   * @param json   raw JSON response
   * @param usSize the expected US size to match in listing titles (may be null)
   * @return lowest matching price in EUR, or empty
   */
  private Optional<Double> parseLowestPrice(String json, String usSize) throws Exception {
    JsonNode root = objectMapper.readTree(json);
    JsonNode response = root.path("findItemsAdvancedResponse").path(0);

    String ack = response.path("ack").path(0).asText();
    if (!"Success".equals(ack) && !"Warning".equals(ack)) {
      String errMsg = response.path("errorMessage").path(0)
          .path("error").path(0).path("message").path(0).asText("unbekannt");
      logger.warn("eBay API Fehler: {} – {}", ack, errMsg);
      return Optional.empty();
    }

    JsonNode items = response.path("searchResult").path(0).path("item");
    if (!items.isArray()) {
      return Optional.empty();
    }

    double lowest = Double.MAX_VALUE;
    boolean found = false;

    for (JsonNode item : items) {
      String title = item.path("title").path(0).asText("");

      // Prüfe ob Titel die Größe enthält (verhindert Fehlkäufe durch falsche Größe)
      if (usSize != null && !titleMatchesSize(title, usSize)) {
        logger.debug("eBay: Überspringe '{}' – Größe {} nicht im Titel", title, usSize);
        continue;
      }

      double price = item.path("sellingStatus").path(0)
          .path("currentPrice").path(0)
          .path("__value__").asDouble(-1);

      if (price > 0 && price < lowest) {
        lowest = price;
        found = true;
      }
    }

    if (found) {
      logger.debug("eBay niedrigster Preis: {} €", lowest);
      return Optional.of(lowest);
    }
    return Optional.empty();
  }

  /**
   * Checks if the listing title contains a size indicator matching the expected US size.
   * Accepts formats like "US 11", "US11", "11 US", "11US", "Size 11".
   *
   * @param title  the eBay listing title
   * @param usSize normalized US size string
   * @return true if the title appears to match the size
   */
  private boolean titleMatchesSize(String title, String usSize) {
    if (usSize == null || usSize.isBlank()) {
      return true;
    }
    String t = title.toLowerCase();
    String s = usSize.toLowerCase();
    // Akzeptiere verschiedene Schreibweisen: "us 11", "us11", "11 us", "size 11", "gr. 11"
    return t.contains("us " + s)
        || t.contains("us" + s)
        || t.contains(s + " us")
        || t.contains(s + "us")
        || t.contains("size " + s)
        || t.contains("gr. " + s)
        || t.contains("gr " + s);
  }

  /**
   * Normalizes the US-size string (strips "US " prefix, validates numeric format).
   *
   * @param usSize raw size from DB
   * @return normalized size or null if unparseable
   */
  private String normalizeUsSize(String usSize) {
    if (usSize == null || usSize.isBlank()) {
      return null;
    }
    String s = usSize.trim();
    if (s.toUpperCase().startsWith("US ")) {
      s = s.substring(3).trim();
    }
    return s.matches("\\d+(\\.\\d+)?") ? s : null;
  }

  /**
   * Performs a GET request to the eBay Finding API.
   *
   * @param url the target URL
   * @return response body as string
   */
  private String get(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("User-Agent", "Treasury/1.0")
        .header("Accept", "application/json")
        .GET()
        .build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }
}
