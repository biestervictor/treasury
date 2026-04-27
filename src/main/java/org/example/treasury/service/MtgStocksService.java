package org.example.treasury.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MtgStocksService fetches sealed product data from the MTGStocks API
 * to resolve booster box image URLs per set code.
 */
@Service
public class MtgStocksService {

  private static final Logger logger = LoggerFactory.getLogger(MtgStocksService.class);
  private static final String SEALED_API_URL = "https://api.mtgstocks.com/sealed";
  private static final String IMAGE_BASE = "https://static.mtgstocks.com/sealedimage/t";

  /**
   * Fetches a map of set code (uppercase) to booster box image URL from MTGStocks.
   * Prefers products with {@code type = "boosterbox"}; falls back to products
   * whose name contains "Booster Box" or "Booster Display".
   *
   * @return map of setCode to boosterBoxImageUrl (never null)
   * @throws Exception if the API call fails
   */
  public Map<String, String> fetchBoosterBoxImageUrls() throws Exception {
    String json = fetch(SEALED_API_URL);
    return parseBoosterBoxImageUrls(json);
  }

  /**
   * Fetches a map of set code (uppercase) to current booster box market price from MTGStocks.
   * Uses the same sealed endpoint; prefers {@code market_price}, falls back to {@code avg30}.
   *
   * @return map of setCode to price in EUR (never null)
   * @throws Exception if the API call fails
   */
  public Map<String, Double> fetchBoosterBoxPrices() throws Exception {
    String json = fetch(SEALED_API_URL);
    return parseBoosterBoxPrices(json);
  }

  /**
   * Parses the MTGStocks sealed JSON array and returns a map of
   * setCode (uppercase) to booster box image URL.
   *
   * @param json the raw JSON string from the MTGStocks sealed endpoint
   * @return map of setCode to boosterBoxImageUrl
   */
  Map<String, String> parseBoosterBoxImageUrls(String json) {
    JSONArray sets = new JSONArray(json);
    Map<String, String> result = new HashMap<>();

    for (int i = 0; i < sets.length(); i++) {
      JSONObject set = sets.getJSONObject(i);
      String abbreviation = set.optString("abbreviation", "");
      if (abbreviation.isEmpty() || !set.has("products")) {
        continue;
      }
      String setCode = abbreviation.toUpperCase();
      JSONArray products = set.getJSONArray("products");

      Integer boosterBoxId = null;
      Integer fallbackId = null;

      for (int j = 0; j < products.length(); j++) {
        JSONObject product = products.getJSONObject(j);
        int pid = product.getInt("id");
        String type = product.optString("type", "");
        String name = product.optString("name", "").toLowerCase();

        if ("boosterbox".equals(type)) {
          boosterBoxId = pid;
          break;
        }
        if (fallbackId == null
            && (name.contains("booster box") || name.contains("booster display"))) {
          fallbackId = pid;
        }
      }

      int finalId = boosterBoxId != null ? boosterBoxId
          : (fallbackId != null ? fallbackId : -1);
      if (finalId > 0) {
        String imageUrl = resolveImageUrl(finalId);
        if (imageUrl != null) {
          result.put(setCode, imageUrl);
        }
      }
    }

    logger.info("Fetched booster box images for {} sets from MTGStocks", result.size());
    return result;
  }

  /**
   * Parses the MTGStocks sealed JSON and returns a map of setCode (uppercase) to booster box
   * market price. Uses {@code market_price} if available, falls back to {@code avg30}.
   *
   * @param json the raw JSON string from the MTGStocks sealed endpoint
   * @return map of setCode to price in EUR
   */
  Map<String, Double> parseBoosterBoxPrices(String json) {
    JSONArray sets = new JSONArray(json);
    Map<String, Double> result = new HashMap<>();

    for (int i = 0; i < sets.length(); i++) {
      JSONObject set = sets.getJSONObject(i);
      String abbreviation = set.optString("abbreviation", "");
      if (abbreviation.isEmpty() || !set.has("products")) {
        continue;
      }
      String setCode = abbreviation.toUpperCase();
      JSONArray products = set.getJSONArray("products");

      double price = -1;
      double fallbackPrice = -1;

      for (int j = 0; j < products.length(); j++) {
        JSONObject product = products.getJSONObject(j);
        String type = product.optString("type", "");
        String name = product.optString("name", "").toLowerCase();

        if ("boosterbox".equals(type)) {
          price = product.optDouble("market_price", -1);
          if (price <= 0) {
            price = product.optDouble("avg30", -1);
          }
          break;
        }
        if (fallbackPrice <= 0
            && (name.contains("booster box") || name.contains("booster display"))) {
          double fp = product.optDouble("market_price", -1);
          if (fp <= 0) {
            fp = product.optDouble("avg30", -1);
          }
          if (fp > 0) {
            fallbackPrice = fp;
          }
        }
      }

      double finalPrice = price > 0 ? price : fallbackPrice;
      if (finalPrice > 0) {
        result.put(setCode, finalPrice);
      }
    }

    logger.info("Fetched booster box prices for {} sets from MTGStocks", result.size());
    return result;
  }

  /**
   * Probes the MTGStocks CDN for a product image, trying .webp first then .png.
   * Returns the URL of the first format that exists, or null if neither is available.
   *
   * @param productId the MTGStocks product ID
   * @return the image URL or null
   */
  String resolveImageUrl(int productId) {
    for (String ext : new String[]{"webp", "png"}) {
      String url = IMAGE_BASE + productId + "." + ext;
      if (probeUrl(url)) {
        return url;
      }
    }
    return null;
  }

  private boolean probeUrl(String urlToProbe) {
    try {
      URL url = new URL(urlToProbe);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("HEAD");
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      conn.setInstanceFollowRedirects(false);
      return conn.getResponseCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  private String fetch(String urlToRead) throws Exception {
    StringBuilder result = new StringBuilder();
    URL url = new URL(urlToRead);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
    conn.setConnectTimeout(15000);
    conn.setReadTimeout(30000);
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      for (String line; (line = reader.readLine()) != null;) {
        result.append(line);
      }
    }
    return result.toString();
  }
}
