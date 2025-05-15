package org.example.treasury;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * TreasuryApplication is the main class that starts the Spring Boot application.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.example.treasury.controller", "org.example.treasury.service",
    "org.example.treasury.model", "org.example.treasury.repository"})

public class TreasuryApplication {

  /**
   * Main method to start the Spring Boot application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(TreasuryApplication.class, args);
  }

}