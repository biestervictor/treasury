package org.example.treasury.service;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.example.treasury.config.MailProperties;
import org.example.treasury.dto.MailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "treasury.mail", name = "enabled", havingValue = "true")
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;

  public MailService(JavaMailSender mailSender, MailProperties mailProperties) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
  }

  /**
   * Versendet eine Plaintext-Mail an eine oder mehrere Empfänger.
   */
  public void send(MailRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    if (!mailProperties.isEnabled()) {
      log.info("Mail ist deaktiviert (treasury.mail.enabled=false). Würde senden an {} mit Subject '{}'.",
          request.getTo(), request.getSubject());
      return;
    }

    if (request.getTo() == null || request.getTo().isEmpty()) {
      throw new IllegalArgumentException("to must not be empty");
    }
    if (request.getSubject() == null || request.getSubject().isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }

    MimeMessage message = mailSender.createMimeMessage();

    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setTo(request.getTo().toArray(new String[0]));
      helper.setSubject(request.getSubject());
      helper.setText(request.getText() == null ? "" : request.getText(), false);

      if (mailProperties.getFrom() != null && !mailProperties.getFrom().isBlank()) {
        helper.setFrom(mailProperties.getFrom());
      }
      if (mailProperties.getReplyTo() != null && !mailProperties.getReplyTo().isBlank()) {
        helper.setReplyTo(mailProperties.getReplyTo());
      }

      // Defensive: some SMTP/test servers are picky about missing From.
      if (message.getFrom() == null || message.getFrom().length == 0) {
        message.setFrom(new InternetAddress("no-reply@localhost"));
      }

      message.setRecipients(Message.RecipientType.TO, message.getRecipients(Message.RecipientType.TO));

      mailSender.send(message);
    } catch (Exception e) {
      throw new IllegalStateException("Could not send mail", e);
    }
  }
}
