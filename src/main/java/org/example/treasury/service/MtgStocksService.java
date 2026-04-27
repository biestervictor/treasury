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
      if (!set.has("abbreviation") || !set.has("products")) {
        continue;
      }
      String setCode = set.getString("abbreviation").toUpperCase();
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
        result.put(setCode, IMAGE_BASE + finalId + ".png");
      }
    }

    logger.info("Fetched booster box images for {} sets from MTGStocks", result.size());
    return result;
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
