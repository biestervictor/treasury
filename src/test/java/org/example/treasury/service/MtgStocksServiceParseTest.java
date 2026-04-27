package org.example.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MtgStocksService#parseBoosterBoxImageUrls(String)}.
 */
class MtgStocksServiceParseTest {

  private MtgStocksService service;

  @BeforeEach
  void setUp() {
    service = new MtgStocksService();
  }

  @Test
  void parse_productWithBoosterboxType_returnsCorrectImageUrl() {
    String json = """
        [{"abbreviation":"IKO","products":[
          {"id":239,"name":"Booster Box","type":"boosterbox"}
        ]}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertEquals(
        "https://static.mtgstocks.com/sealedimage/t239.png",
        result.get("IKO"));
  }

  @Test
  void parse_setCodeIsUppercased() {
    String json = """
        [{"abbreviation":"iko","products":[
          {"id":239,"name":"Booster Box","type":"boosterbox"}
        ]}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertTrue(result.containsKey("IKO"));
    assertFalse(result.containsKey("iko"));
  }

  @Test
  void parse_fallbackByBoosterBoxName_returnsImageUrl() {
    String json = """
        [{"abbreviation":"ZNR","products":[
          {"id":500,"name":"Zendikar Rising Booster Box","type":null}
        ]}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertEquals(
        "https://static.mtgstocks.com/sealedimage/t500.png",
        result.get("ZNR"));
  }

  @Test
  void parse_fallbackByBoosterDisplayName_returnsImageUrl() {
    String json = """
        [{"abbreviation":"MH3","products":[
          {"id":999,"name":"Play Booster Display","type":null}
        ]}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertEquals(
        "https://static.mtgstocks.com/sealedimage/t999.png",
        result.get("MH3"));
  }

  @Test
  void parse_boosterboxTypeTakesPriorityOverNameFallback() {
    String json = """
        [{"abbreviation":"KHM","products":[
          {"id":10,"name":"Kaldheim Booster Box","type":null},
          {"id":20,"name":"Kaldheim Booster Box","type":"boosterbox"}
        ]}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertEquals(
        "https://static.mtgstocks.com/sealedimage/t20.png",
        result.get("KHM"));
  }

  @Test
  void parse_noMatchingProduct_setNotInResult() {
    String json = """
        [{"abbreviation":"CMD","products":[
          {"id":1,"name":"Commander Deck","type":null}
        ]}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertFalse(result.containsKey("CMD"));
  }

  @Test
  void parse_missingProductsField_setSkipped() {
    String json = """
        [{"abbreviation":"OLD"}]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertTrue(result.isEmpty());
  }

  @Test
  void parse_multipleSets_allParsed() {
    String json = """
        [
          {"abbreviation":"IKO","products":[{"id":239,"name":"BB","type":"boosterbox"}]},
          {"abbreviation":"KHM","products":[{"id":350,"name":"BB","type":"boosterbox"}]}
        ]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertEquals(2, result.size());
  }

  @Test
  void parse_nullAbbreviation_setSkipped() {
    String json = """
        [
          {"abbreviation":null,"products":[{"id":1,"name":"BB","type":"boosterbox"}]},
          {"abbreviation":"IKO","products":[{"id":239,"name":"BB","type":"boosterbox"}]}
        ]""";

    Map<String, String> result = service.parseBoosterBoxImageUrls(json);

    assertEquals(1, result.size());
    assertEquals("https://static.mtgstocks.com/sealedimage/t239.png", result.get("IKO"));
  }
}
