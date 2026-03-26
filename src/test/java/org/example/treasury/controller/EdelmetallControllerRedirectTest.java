package org.example.treasury.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.treasury.dto.ManualMetalPricesRequest;
import org.example.treasury.service.EdelmetallService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

class EdelmetallControllerRedirectTest {

  @Test
  void updatePrices_redirectsToDashboardView() {
    EdelmetallService service = Mockito.mock(EdelmetallService.class);
    EdelmetallController controller = new EdelmetallController(service);

    Mockito.when(service.updatePricesAndStoreValuation()).thenReturn(null);

    ResponseEntity<String> resp = controller.updatePrices();
    assertThat(resp.getStatusCode().value()).isEqualTo(303);
    assertThat(resp.getHeaders().getFirst("Location")).isEqualTo("/api/edelmetall/dashboard/view");
  }

  @Test
  void manualPricesView_redirectsToDashboardView() {
    EdelmetallService service = Mockito.mock(EdelmetallService.class);
    EdelmetallController controller = new EdelmetallController(service);

    Mockito.when(service.storeManualPricesAndValuation(Mockito.any())).thenReturn(null);

    ResponseEntity<String> resp = controller.manualPricesView(new ManualMetalPricesRequest("1", "1"));
    assertThat(resp.getStatusCode().value()).isEqualTo(303);
    assertThat(resp.getHeaders().getFirst("Location")).isEqualTo("/api/edelmetall/dashboard/view");
  }
}

