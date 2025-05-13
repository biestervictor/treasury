package org.example.treasury.service;

import org.example.treasury.model.MagicSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScryFallWebservice {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    public List<MagicSet> getSetList() throws Exception {
        String next_page = "";
        boolean has_more = false;
        boolean newSet = false;
        List<MagicSet> magicSets = new ArrayList<>();


        String scryfallURL = "https://api.scryfall.com/sets";
        do {
            String apiCallURL;
            if (has_more) {
                apiCallURL = next_page;
                next_page = "";
            } else {
                apiCallURL = scryfallURL;
            }
            String jsonString = getHTML(apiCallURL);

            JSONObject obj = new JSONObject(jsonString);
            has_more = obj.getBoolean("has_more");
            if (has_more) {
                next_page = obj.getString("next_page");
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
        } while (has_more);


        return magicSets;
    }

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