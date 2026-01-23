package org.example.treasury.dto;

import java.util.List;

/**
 * Einfacher Request zum Versenden einer Mail.
 */
public class MailRequest {

  private final List<String> to;
  private final String subject;
  private final String text;

  public MailRequest(List<String> to, String subject, String text) {
    this.to = to;
    this.subject = subject;
    this.text = text;
  }

  public List<String> getTo() {
    return to;
  }

  public String getSubject() {
    return subject;
  }

  public String getText() {
    return text;
  }
}
