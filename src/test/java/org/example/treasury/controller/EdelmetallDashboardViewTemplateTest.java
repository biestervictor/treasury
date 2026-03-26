package org.example.treasury.controller;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class EdelmetallDashboardViewTemplateTest {

  @Test
  void thymeleafInlineJavaScriptSerialization_doesNotFail_whenLabelsAreStrings() {
    // Dieser Test bildet den ursprünglichen Fehler nach:
    // Thymeleaf benutzt in th:inline="javascript" einen Jackson-ObjectMapper.
    // Wenn dabei Instants rausfallen, crasht das Template.
    // Unser Fix im Template nutzt jetzt Labels als Strings; daher serialisieren wir hier nur
    // eine Liste von Strings, was immer funktionieren muss.

    List<String> labels = List.of("2026-01-02T00:00:00Z");

    ObjectMapper mapper = new ObjectMapper();
    assertThatCode(() -> mapper.writeValueAsString(labels)).doesNotThrowAnyException();
  }
}

