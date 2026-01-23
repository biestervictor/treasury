package org.example.treasury.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration für ausgehende E-Mails.
 *
 * <p>Standard-SMTP-Parameter kommen über spring.mail.*.
 * Diese Properties sind app-spezifische Defaults.</p>
 */
@ConfigurationProperties(prefix = "treasury.mail")
public class MailProperties {

  private boolean enabled = false;
  private String from;
  private String replyTo;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public String getReplyTo() {
    return replyTo;
  }

  public void setReplyTo(String replyTo) {
    this.replyTo = replyTo;
  }
}
