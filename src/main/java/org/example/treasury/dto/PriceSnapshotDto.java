package org.example.treasury.dto;

/**
 * DTO for a single price data point returned by the history REST API.
 *
 * @param date  ISO-8601 date string (e.g. "2025-01-15")
 * @param price scraped relevant market price in EUR
 */
public record PriceSnapshotDto(String date, double price) {
}
