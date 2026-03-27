package org.example.treasury.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.example.treasury.model.JobSchedule;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class JobScheduleService {

  public Instant nextRun(JobSchedule schedule, Instant now) {
    if (schedule == null) {
      return null;
    }
    if (!"cron".equalsIgnoreCase(schedule.type())) {
      return null;
    }
    String cron = schedule.cron();
    if (cron == null || cron.isBlank()) {
      return null;
    }

    ZoneId zoneId = schedule.zoneId() != null ? schedule.zoneId() : ZoneId.systemDefault();
    CronExpression exp = CronExpression.parse(cron);
    ZonedDateTime next = exp.next(ZonedDateTime.ofInstant(now, zoneId));
    return next != null ? next.toInstant() : null;
  }
}

