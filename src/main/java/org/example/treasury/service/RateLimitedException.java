package org.example.treasury.service;

/**
 * Wird geworfen, wenn Cardmarket den Request aufgrund von Rate-Limits ablehnt (z.B. Error 1015).
 */
public class RateLimitedException extends RuntimeException {

  public RateLimitedException(String message) {
    super(message);
  }

  public RateLimitedException(String message, Throwable cause) {
    super(message, cause);
  }
}
