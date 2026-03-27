package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.example.treasury.model.JobSchedule;
import org.junit.jupiter.api.Test;

class JobScheduleServiceTest {

  @Test
  void nextRun_returnsNextInstantForDailyMidnightCron() {
    JobScheduleService svc = new JobScheduleService();
    Instant now = Instant.parse("2026-03-27T10:15:30Z");

    Instant next = svc.nextRun(JobSchedule.cron("0 0 0 * * *", "täglich 00:00"), now);

    assertThat(next).isNotNull();
    assertThat(next).isAfter(now);
  }

  @Test
  void nextRun_returnsNullOnBlankCron() {
    JobScheduleService svc = new JobScheduleService();
    assertThat(svc.nextRun(JobSchedule.cron(" ", ""), Instant.now())).isNull();
  }
}

