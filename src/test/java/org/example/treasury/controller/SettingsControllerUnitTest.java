package org.example.treasury.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.example.treasury.config.JobSettingsProperties;
import org.example.treasury.service.JobSettingsViewService;
import org.example.treasury.service.JobTriggerService;
import org.example.treasury.service.JobSettingsService;
import org.example.treasury.service.JobRuntimeSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class SettingsControllerUnitTest {

  @Test
  void settings_addsModelAttributes_andReturnsViewName() {
    JobSettingsService service = Mockito.mock(JobSettingsService.class);
    JobSettingsViewService viewService = Mockito.mock(JobSettingsViewService.class);
    JobTriggerService triggerService = Mockito.mock(JobTriggerService.class);
    JobRuntimeSettingsService runtime = Mockito.mock(JobRuntimeSettingsService.class);

    JobSettingsProperties props = new JobSettingsProperties();
    Instant updatedAt = Instant.parse("2026-01-28T12:34:56Z");

    when(service.get()).thenReturn(props);
    when(service.getUpdatedAt()).thenReturn(updatedAt);

    when(viewService.list()).thenReturn(java.util.List.of());
    SettingsController controller = new SettingsController(service, viewService, triggerService, runtime);
    Model model = new ExtendedModelMap();

    String view = controller.settings(model);

    assertEquals("settings", view);
    assertEquals(props, model.getAttribute("settings"));
    assertEquals(updatedAt, model.getAttribute("updatedAt"));
    assertEquals(java.util.List.of(), model.getAttribute("jobs"));
  }

  @Test
  void updateJobs_updatesService_setsFlash_andRedirects() {
    JobSettingsService service = Mockito.mock(JobSettingsService.class);
    JobSettingsViewService viewService = Mockito.mock(JobSettingsViewService.class);
    JobTriggerService triggerService = Mockito.mock(JobTriggerService.class);
    JobRuntimeSettingsService runtime = Mockito.mock(JobRuntimeSettingsService.class);
    when(service.get()).thenReturn(new JobSettingsProperties());
    when(runtime.get(Mockito.any())).thenReturn(null);
    SettingsController controller = new SettingsController(service, viewService, triggerService, runtime);

    RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

    String view = controller.updateJobs(true, false, true, true, false,
        "0 0 0 * * *", "0 0 0 * * *", "0 0 0 * * *", "0 0 */6 * * *", "0 0 8 * * MON",
        null,
        redirectAttributes);

    assertEquals("redirect:/api/settings", view);
    assertEquals("Job-Einstellungen gespeichert.", redirectAttributes.getFlashAttributes().get("success"));
    verify(service).update(true, false, true, true, false);
  }
}
