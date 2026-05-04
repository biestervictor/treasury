package org.example.treasury.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.treasury.dto.ManualMetalPricesRequest;
import org.example.treasury.job.CollectorCoinPriceJob;
import org.example.treasury.repository.PreciousMetalRepository;
import org.example.treasury.service.CollectorCoinPricingService;
import org.example.treasury.service.EdelmetallService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

class EdelmetallControllerRedirectTest {

  private EdelmetallController buildController() {
    EdelmetallService service = Mockito.mock(EdelmetallService.class);
    CollectorCoinPricingService pricingService = Mockito.mock(CollectorCoinPricingService.class);
    CollectorCoinPriceJob job = Mockito.mock(CollectorCoinPriceJob.class);
    PreciousMetalRepository repo = Mockito.mock(PreciousMetalRepository.class);
    return new EdelmetallController(service, pricingService, job, repo);
  }

  @Test
  void updatePrices_redirectsToDashboardView() {
    EdelmetallService service = Mockito.mock(EdelmetallService.class);
    CollectorCoinPricingService pricingService = Mockito.mock(CollectorCoinPricingService.class);
    CollectorCoinPriceJob job = Mockito.mock(CollectorCoinPriceJob.class);
    PreciousMetalRepository repo = Mockito.mock(PreciousMetalRepository.class);
    EdelmetallController controller = new EdelmetallController(service, pricingService, job, repo);

    Mockito.when(service.updatePricesAndStoreValuation()).thenReturn(null);

    ResponseEntity<String> resp = controller.updatePrices();
    assertThat(resp.getStatusCode().value()).isEqualTo(303);
    assertThat(resp.getHeaders().getFirst("Location")).isEqualTo("/api/edelmetall/dashboard/view");
  }

  @Test
  void manualPricesView_redirectsToDashboardView() {
    EdelmetallController controller = buildController();

    ResponseEntity<String> resp = controller.manualPricesView(new ManualMetalPricesRequest("1", "1"));
    assertThat(resp.getStatusCode().value()).isEqualTo(303);
    assertThat(resp.getHeaders().getFirst("Location")).isEqualTo("/api/edelmetall/dashboard/view");
  }
}

