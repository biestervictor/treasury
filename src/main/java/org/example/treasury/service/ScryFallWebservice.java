package org.example.treasury.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.example.treasury.model.MagicSet;
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

  public ScryFallWebservice(MagicSetService magicSetService) {
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
        //Filter digitale Sets
        if ((!jsonCard.getBoolean("digital") && jsonCard.getInt("card_count") > 0)) {
          magicSets.add(MagicSet.builder().name(jsonCard.getString("name"))
              .code(jsonCard.getString("code"))
              .uri(jsonCard.getString("uri"))
              .iconUri(jsonCard.getString("icon_svg_uri"))
              .releaseDate(LocalDate.parse(jsonCard.getString("released_at")))
              .cardCount(jsonCard.getInt("card_count")).build());


        }
      }
    } while (hasMore);


    return magicSets;
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