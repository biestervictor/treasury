package org.example.treasury.service;

import java.util.List;
import java.util.Optional;
import org.example.treasury.dto.MailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Versendet nach erfolgreichem App-Startup eine Benachrichtigungs-Mail.
 */
@Component
public class StartupMailNotifier {

  private static final Logger log = LoggerFactory.getLogger(StartupMailNotifier.class);

  private final Optional<MailService> mailService;

  @Value("${treasury.mail.startup.to:}")
  private String to;

  @Value("${treasury.mail.startup.enabled:true}")
  private boolean enabled;

  public StartupMailNotifier(Optional<MailService> mailService) {
    this.mailService = mailService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (!enabled) {
      log.info("StartupMailNotifier deaktiviert (treasury.mail.startup.enabled=false)");
      return;
    }

    if (to == null || to.isBlank()) {
      log.debug("StartupMailNotifier: treasury.mail.startup.to ist leer – keine Mail.");
      return;
    }

    mailService.ifPresentOrElse(service -> {
      try {
        service.send(new MailRequest(List.of(to), "Treasury gestartet", "Die Treasury-Anwendung ist gestartet."));
        log.info("StartupMailNotifier: Startup-Mail gesendet an {}", to);
      } catch (Exception e) {
        // Startup darf nicht wegen Mail fehlschlagen
        log.warn("StartupMailNotifier: konnte Startup-Mail nicht senden", e);
      }
    }, () -> log.info("StartupMailNotifier: MailService nicht aktiv (treasury.mail.enabled=false)"));
  }
}
