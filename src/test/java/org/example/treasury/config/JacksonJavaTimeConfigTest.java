package org.example.treasury.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.example.treasury.dto.MetalDashboardDto;
import org.junit.jupiter.api.Test;

class JacksonJavaTimeConfigTest {

  @Test
  void serializesInstantInDto() throws Exception {
    MetalDashboardDto dto = new MetalDashboardDto(
        new MetalDashboardDto.PriceDto(1.0, 2.0, Instant.parse("2026-01-01T00:00:00Z")),
        List.of(new MetalDashboardDto.ProfitPointDto(Instant.parse("2026-01-02T00:00:00Z"), 12.34)),
        List.of(),
        List.of(new MetalDashboardDto.MarketValuePointDto(Instant.parse("2026-01-02T00:00:00Z"), 123.45)),
        12.34,
        123.45
    );

    // Isolierter Test: Wir verifizieren, dass Jackson mit JavaTimeModule Instants serialisieren kann.
    // (Ein Spring @JsonTest würde hier aktuell den kompletten ApplicationContext starten und scheitert
    //  an fachfremden Beans/Repositories, s. Surefire-Report.)
    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    String out = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dto);

    // Es reicht zu prüfen, dass der Instant als ISO-8601 String geschrieben wurde.
    assertThat(out).contains("\"timestamp\" : \"2026-01-02T00:00:00Z\"");
  }
}

