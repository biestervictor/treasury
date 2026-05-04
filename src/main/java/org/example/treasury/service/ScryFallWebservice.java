package org.example.treasury.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.example.treasury.model.MagicSet;
import org.example.treasury.model.SetType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ScryFallWebservice is a service class that interacts
 * with the ScryFall API to fetch Magic: The Gathering set data.
 */

@Service

public class ScryFallWebservice {
  private final MagicSetService magicSetService;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructor for ScryFallWebservice.
   *
   * @param magicSetService the MagicSetService instance
   */

  public ScryFallWebservice(MagicSetService magicSetService, DisplayService displayService) {
    this.magicSetService = magicSetService;
  }

  /**
   * getSetList fetches a list of Magic: The Gathering sets from the ScryFall API.
   *
   * @return a list of MagicSet objects
   * @throws Exception if an error occurs while fetching data from the API
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public List<MagicSet> getSetList() throws Exception {
    String nextPage = "";
    boolean hasMore = false;
    boolean newSet = false;
    List<MagicSet> magicSets = new ArrayList<>();

    Set<String> ubSetCodes;
    try {
      ubSetCodes = getUniverseBeyondSetCodes();
    } catch (Exception e) {
      logger.warn("Universe-Beyond-SetCodes konnten nicht ermittelt werden", e);
      ubSetCodes = new HashSet<>();
    }

    String scryfallURL = "https://api.scryfall.com/sets";
    do {
      String apiCallURL;
      if (hasMore) {
        apiCallURL = nextPage;
        nextPage = "";
      } else {
        apiCallURL = scryfallURL;
      }
      String jsonString = getHTML(apiCallURL);

      JSONObject obj = new JSONObject(jsonString);
      hasMore = obj.getBoolean("has_more");
      if (hasMore) {
        nextPage = obj.getString("next_page");
      }
      JSONArray data = obj.getJSONArray("data");

      for (int i = 0; i < data.length(); i++) {
        JSONObject jsonCard = data.getJSONObject(i);
        String setCode = jsonCard.getString("code");
        String setType=jsonCard.getString("set_type");

        //Filter digitale Sets und füge bestehende Commander Sets vom Type "Commander" hinzu. Diese werden nicht
        if (setCode.equals("who")|| SetType.containsValue(setType)
            && !jsonCard.getBoolean("digital")
            && jsonCard.getInt("card_count") > 0) {
          magicSets.add(MagicSet.builder().name(jsonCard.getString("name"))
              .code(jsonCard.getString("code").toUpperCase())
              .uri(jsonCard.getString("uri"))
              .iconUri(jsonCard.getString("icon_svg_uri"))
              .releaseDate(LocalDate.parse(jsonCard.getString("released_at")))
              .cardCount(jsonCard.getInt("card_count")).setType(setType)
              .universeBeyond(ubSetCodes.contains(setCode.toUpperCase()))
              .build());


        }
      }
    } while (hasMore);


    return magicSets;
  }

  /**
   * Fetches the set codes of all Universe Beyond sets from the Scryfall cards search API.
   * Uses {@code is:universesbeyond} with {@code unique=sets} to retrieve one card per UB set.
   *
   * @return a set of uppercase set codes that belong to Universe Beyond
   * @throws Exception if an error occurs while fetching data from the API
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public Set<String> getUniverseBeyondSetCodes() throws Exception {
    Set<String> ubSetCodes = new HashSet<>();
    String nextPage =
        "https://api.scryfall.com/cards/search?q=is:universesbeyond&unique=sets";
    boolean hasMore = true;
    while (hasMore) {
      String jsonString = getHTML(nextPage);
      JSONObject obj = new JSONObject(jsonString);
      hasMore = obj.optBoolean("has_more", false);
      if (hasMore) {
        nextPage = obj.getString("next_page");
      }
      JSONArray data = obj.getJSONArray("data");
      for (int i = 0; i < data.length(); i++) {
        JSONObject card = data.getJSONObject(i);
        ubSetCodes.add(card.getString("set").toUpperCase());
      }
    }
    return ubSetCodes;
  }

  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private String getHTML(String urlToRead) throws Exception {
    StringBuilder result = new StringBuilder();
    URL url = new URL(urlToRead);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      for (String line; (line = reader.readLine()) != null; ) {
        result.append(line);
      }
    }
    return result.toString();
  }
}