package org.example.treasury.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.example.treasury.model.Shoe;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetches the lowest Klekt listing price (Ask) and highest bid for a specific shoe
 * and US size using the public Klekt API (api.k-v3.com) without authentication.
 *
 * <p>Flow:
 * <ol>
 *   <li>Fetch the Klekt product page HTML and extract the product-variant ID
 *       from the embedded schema.org JSON-LD.</li>
 *   <li>Call the size-chart endpoint to resolve the US size to a {@code sizeItemId}.</li>
 *   <li>Call the listings endpoint to get the lowest Ask price.</li>
 *   <li>Call the bids endpoint to get the highest Bid price.</li>
 * </ol>
 */
@Service
public class ShoePriceCollectorService {

  /**
   * Holds both the lowest Ask and highest Bid from Klekt for a given shoe/size combination.
   *
   * @param ask lowest listing price in EUR (0 if unavailable)
   * @param bid highest buy-order price in EUR (0 if unavailable)
   */
  public record KlektPriceData(double ask, double bid) {
  }

  private static final Logger logger =
      LoggerFactory.getLogger(ShoePriceCollectorService.class);

  private static final String KLEKT_BASE = "https://klekt.com/product/new/";
  private static final String API_BASE = "https://api.k-v3.com";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /**
   * Constructor.
   */
  public ShoePriceCollectorService() {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Fetches both the lowest Ask and highest Bid from Klekt for the given shoe at its US size.
   * Returns empty if the shoe has no klektSlug or the product cannot be resolved.
   * If only one side (ask/bid) fails, the other is still returned with 0 for the failed side.
   *
   * @param shoe the shoe to price
   * @return KlektPriceData with ask and bid, or empty if the product cannot be found at all
   */
  public Optional<KlektPriceData> fetchPrices(Shoe shoe) {
    String slug = shoe.getKlektSlug();
    if (slug == null || slug.isBlank()) {
      logger.debug("Skipping shoe {} – no klektSlug set", shoe.getId());
      return Optional.empty();
    }

    String usSize = normalizeUsSize(shoe.getUsSize());
    if (usSize == null) {
      logger.warn("Cannot parse US size '{}' for shoe {}", shoe.getUsSize(), shoe.getId());
      return Optional.empty();
    }

    try {
      String productVariantId = fetchProductVariantId(slug);
      if (productVariantId == null) {
        logger.warn("Could not find productVariantId for slug {}", slug);
        return Optional.empty();
      }

      String sizeItemId = fetchSizeItemId(productVariantId, usSize);
      if (sizeItemId == null) {
        logger.warn("No sizeItemId for US {} on product {}", usSize, productVariantId);
        return Optional.empty();
      }

      double ask = fetchMinListingPrice(productVariantId, sizeItemId).orElse(0.0);
      double bid = fetchMaxBidPrice(productVariantId, sizeItemId).orElse(0.0);
      return Optional.of(new KlektPriceData(ask, bid));

    } catch (Exception e) {
      logger.error("Error fetching Klekt prices for slug {}: {}", slug, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Returns the lowest Klekt listing price in EUR for the given shoe at its US size,
   * or an empty Optional if no listing is found or the shoe has no klektSlug.
   *
   * @param shoe the shoe to price
   * @return lowest listing price in EUR, or empty
   */
  public Optional<Double> fetchLowestPrice(Shoe shoe) {
    return fetchPrices(shoe).map(KlektPriceData::ask).filter(p -> p > 0);
  }

  /**
   * Fetches the Klekt product page and extracts the product-variant ID from the
   * embedded schema.org JSON-LD {@code "@id"} field.
   *
   * @param slug the Klekt product slug
   * @return the product-variant UUID, or null if not found
   */
  private String fetchProductVariantId(String slug) throws Exception {
    String url = KLEKT_BASE + slug;
    String html = get(url);

    Document doc = Jsoup.parse(html);
    Elements scripts = doc.select("script[type=application/ld+json]");
    for (Element script : scripts) {
      String json = script.html();
      JsonNode node = objectMapper.readTree(json);
      if ("Product".equals(node.path("@type").asText())) {
        String id = node.path("@id").asText();
        // id looks like "/product/<uuid>"
        if (id.contains("/")) {
          return id.substring(id.lastIndexOf('/') + 1);
        }
      }
    }
    return null;
  }

  /**
   * Calls the Klekt size-chart API for the given product-variant and returns
   * the {@code sizeItemId} for the requested US size.
   *
   * @param productVariantId the product-variant UUID
   * @param usSize           the US size string (e.g. "11", "10.5")
   * @return the size-item UUID, or null if not found
   */
  private String fetchSizeItemId(String productVariantId, String usSize) throws Exception {
    String url = API_BASE + "/api/v1/size_chart/size_items"
        + "?product_variant_id=" + productVariantId;
    String json = getApi(url);

    JsonNode root = objectMapper.readTree(json);
    for (JsonNode item : root.path("data")) {
      String us = item.path("attributes").path("data").path("us").asText();
      if (usSize.equals(us)) {
        return item.path("id").asText();
      }
    }
    return null;
  }

  /**
   * Calls the Klekt listings API filtered by size and returns the lowest Ask (sale) price.
   *
   * @param productVariantId the product-variant UUID
   * @param sizeItemId       the size-item UUID
   * @return lowest listing price in EUR, or empty if no listings
   */
  private Optional<Double> fetchMinListingPrice(
      String productVariantId, String sizeItemId) throws Exception {
    String url = API_BASE + "/api/v1/product_variants/" + productVariantId
        + "/listings?sizeItemId=" + sizeItemId;
    String json = getApi(url);

    JsonNode root = objectMapper.readTree(json);
    double min = Double.MAX_VALUE;
    boolean found = false;
    for (JsonNode listing : root.path("data")) {
      double price = listing.path("attributes").path("sale_price").asDouble(-1);
      if (price > 0 && price < min) {
        min = price;
        found = true;
      }
    }
    return found ? Optional.of(min) : Optional.empty();
  }

  /**
   * Calls the Klekt bids (buy-orders) API filtered by size and returns the highest Bid price.
   * Returns empty on any error so callers can degrade gracefully.
   *
   * @param productVariantId the product-variant UUID
   * @param sizeItemId       the size-item UUID
   * @return highest bid price in EUR, or empty if no bids or endpoint unavailable
   */
  private Optional<Double> fetchMaxBidPrice(
      String productVariantId, String sizeItemId) {
    try {
      String url = API_BASE + "/api/v1/product_variants/" + productVariantId
          + "/bids?sizeItemId=" + sizeItemId;
      String json = getApi(url);

      JsonNode root = objectMapper.readTree(json);
      double max = 0;
      boolean found = false;
      // Versuche gängige Feldnamen für den Gebotspreise
      for (JsonNode bid : root.path("data")) {
        JsonNode attrs = bid.path("attributes");
        double price = attrs.path("bid_price").asDouble(-1);
        if (price <= 0) {
          price = attrs.path("offer_price").asDouble(-1);
        }
        if (price > 0 && price > max) {
          max = price;
          found = true;
        }
      }
      return found ? Optional.of(max) : Optional.empty();
    } catch (Exception e) {
      logger.debug("Bid-Abfrage fehlgeschlagen für {}: {}", productVariantId, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Normalizes the US-size string coming from the database ("11", "10.5", etc.)
   * to match the Klekt API format.
   *
   * @param usSize raw size from DB (e.g. "US 11", "11", "10.5")
   * @return normalized size string, or null if unparseable
   */
  private String normalizeUsSize(String usSize) {
    if (usSize == null || usSize.isBlank()) {
      return null;
    }
    // Strip leading "US " prefix if present
    String s = usSize.trim();
    if (s.toUpperCase().startsWith("US ")) {
      s = s.substring(3).trim();
    }
    // Validate: must be numeric (integer or decimal)
    if (s.matches("\\d+(\\.\\d+)?")) {
      return s;
    }
    return null;
  }

  /**
   * Performs a GET request to a Klekt product page with browser-like headers.
   *
   * @param url the target URL
   * @return response body as string
   */
  private String get(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.5")
        .GET()
        .build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }

  /**
   * Performs a GET request to the Klekt API with appropriate headers.
   *
   * @param url the target API URL
   * @return response body as string
   */
  private String getApi(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("User-Agent", USER_AGENT)
        .header("Origin", "https://klekt.com")
        .header("Referer", "https://klekt.com/")
        .header("Accept", "application/json")
        .GET()
        .build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }
}
