package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EdelmetallServiceManualPricesParseTest {

  @Test
  void parseEuroNumber_supportsCommaAndDot() {
    assertThat(EdelmetallService.parseEuroNumber("3864,38")).isEqualTo(3864.38);
    assertThat(EdelmetallService.parseEuroNumber("3864.38")).isEqualTo(3864.38);
  }

  @Test
  void parseEuroNumber_supportsThousandsSeparator() {
    assertThat(EdelmetallService.parseEuroNumber("3.864,38")).isEqualTo(3864.38);
  }

  @Test
  void parseEuroNumber_returnsNullOnInvalid() {
    assertThat(EdelmetallService.parseEuroNumber(null)).isNull();
    assertThat(EdelmetallService.parseEuroNumber(" ")).isNull();
    assertThat(EdelmetallService.parseEuroNumber("abc")).isNull();
  }
}

