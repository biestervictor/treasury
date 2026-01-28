package org.example.treasury.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.example.treasury.model.Angebot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public abstract class PriceCollectorService {

  Logger logger = LoggerFactory.getLogger(this.getClass());

  private final Clock clock = Clock.systemUTC();

  // ------------------------------------
  // Jitter (bestehend)
  // ------------------------------------

  @Value("${treasury.scraper.jitter.enabled:true}")
  private boolean jitterEnabled;

  /** Minimum random delay before each request (ms). */
  @Value("${treasury.scraper.jitter.minMs:750}")
  private int jitterMinMs;

  /** Maximum random delay before each request (ms). */
  @Value("${treasury.scraper.jitter.maxMs:2500}")
  private int jitterMaxMs;

  // ------------------------------------
  // Global throttle (neu)
  // ------------------------------------

  @Value("${treasury.scraper.throttle.enabled:true}")
  private boolean throttleEnabled;

  /** Mindestabstand zwischen Requests (ms) – gilt global über Threads hinweg. */
  @Value("${treasury.scraper.throttle.minIntervalMs:2000}")
  private long throttleMinIntervalMs;

  private static final AtomicLong nextAllowedRequestAtMillis = new AtomicLong(0);

  // ------------------------------------
  // Per-URL Cache + Cooldown (neu)
  // ------------------------------------

  @Value("${treasury.scraper.cache.enabled:true}")
  private boolean cacheEnabled;

  /** TTL für cached Angebote pro URL (Sekunden). */
  @Value("${treasury.scraper.cache.ttlSeconds:900}")
  private long cacheTtlSeconds;

  /** Wenn true: bei Fehlern ggf. stale Cache zurückgeben. */
  @Value("${treasury.scraper.cache.serveStaleOnError:true}")
  private boolean serveStaleOnError;

  /** Max Alter (Sekunden) für stale Cache (nur wenn serveStaleOnError=true). */
  @Value("${treasury.scraper.cache.maxStaleSeconds:3600}")
  private long maxStaleSeconds;

  private final ConcurrentHashMap<String, CacheEntry> offerCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<List<Angebot>>> inFlight = new ConcurrentHashMap<>();

  private static final class CacheEntry {
    private final List<Angebot> value;
    private final long fetchedAtMillis;
    private final long expiresAtMillis;

    private CacheEntry(List<Angebot> value, long fetchedAtMillis, long expiresAtMillis) {
      this.value = value;
      this.fetchedAtMillis = fetchedAtMillis;
      this.expiresAtMillis = expiresAtMillis;
    }

    private boolean isFresh(long nowMillis) {
      return nowMillis <= expiresAtMillis;
    }

    private boolean isUsableStale(long nowMillis, long maxStaleMillis) {
      return nowMillis - fetchedAtMillis <= maxStaleMillis;
    }
  }

  // ------------------------------------
  // Backoff/Cooldown bei Rate-Limit (neu)
  // ------------------------------------

  @Value("${treasury.scraper.backoff.enabled:true}")
  private boolean backoffEnabled;

  @Value("${treasury.scraper.backoff.baseDelayMs:15000}")
  private long backoffBaseDelayMs;

  @Value("${treasury.scraper.backoff.maxDelayMs:300000}")
  private long backoffMaxDelayMs;

  /** Wie viele RateLimit-Hits wir tolerieren, bevor wir hart abbrechen. */
  @Value("${treasury.scraper.backoff.maxRateLimitHits:5}")
  private int maxRateLimitHits;

  private static final AtomicLong globalCooldownUntilMillis = new AtomicLong(0);
  private static final AtomicInteger consecutiveRateLimitHits = new AtomicInteger(0);

  protected List<Angebot> requestOffers(BrowserContext context, String url) {

    long now = clock.millis();

    CacheEntry cached = offerCache.get(url);
    if (cacheEnabled && cached != null && cached.isFresh(now)) {
      logger.debug("Scraper cache HIT for {}", url);
      return cached.value;
    }

    if (!cacheEnabled) {
      return fetchOffersWithControls(context, url, cached);
    }

    // Single-flight pro URL
    CompletableFuture<List<Angebot>> created = new CompletableFuture<>();
    CompletableFuture<List<Angebot>> existing = inFlight.putIfAbsent(url, created);
    CompletableFuture<List<Angebot>> future = existing == null ? created : existing;

    if (existing != null) {
      logger.debug("Scraper cache WAIT (in-flight) for {}", url);
      try {
        return future.join();
      } catch (Exception e) {
        // Wenn der in-flight Request scheitert, versuchen wir stale cache.
        if (serveStaleOnError && cached != null) {
          long maxStaleMillis = Math.max(0, maxStaleSeconds) * 1000L;
          if (cached.isUsableStale(clock.millis(), maxStaleMillis)) {
            logger.warn("Scraper in-flight failed for {} – serving STALE cache (age={}ms)", url,
                (clock.millis() - cached.fetchedAtMillis));
            return cached.value;
          }
        }
        throw e;
      }
    }

    // Owner: fetch + cache put
    try {
      List<Angebot> fetched = fetchOffersWithControls(context, url, cached);
      long fetchedAt = clock.millis();
      long ttlMillis = Math.max(0, cacheTtlSeconds) * 1000L;
      offerCache.put(url, new CacheEntry(fetched, fetchedAt, fetchedAt + ttlMillis));
      future.complete(fetched);
      return fetched;
    } catch (Exception e) {
      future.completeExceptionally(e);
      throw e;
    } finally {
      inFlight.remove(url);
    }
  }

  private List<Angebot> fetchOffersWithControls(BrowserContext context, String url, CacheEntry cached) {
    // Erst: globaler Cooldown (Backoff)
    applyGlobalCooldownIfNeeded();

    // Dann: globaler Throttle
    applyGlobalThrottleIfEnabled();

    // Dann: Jitter
    applyJitterIfEnabled(url);

    // Dann: eigentlicher Scrape
    try {
      List<Angebot> offers = scrapeOffers(context, url);
      // Erfolg: Backoff-Status zurücksetzen
      resetBackoffOnSuccess();
      return offers;
    } catch (RuntimeException e) {
      // RateLimit? => Backoff starten
      boolean rateLimited = isRateLimitIndication(e);
      if (rateLimited) {
        onRateLimitHit(url);
      }

      // Bei Fehlern optional stale cache zurückgeben
      if (serveStaleOnError && cached != null) {
        long maxStaleMillis = Math.max(0, maxStaleSeconds) * 1000L;
        if (cached.isUsableStale(clock.millis(), maxStaleMillis)) {
          logger.warn("Scraper fetch failed for {} – serving STALE cache (age={}ms)", url,
              (clock.millis() - cached.fetchedAtMillis));
          return cached.value;
        }
      }

      throw e;
    }
  }

  private void applyJitterIfEnabled(String url) {
    if (!jitterEnabled) {
      return;
    }
    int min = Math.max(0, jitterMinMs);
    int max = Math.max(min, jitterMaxMs);
    int sleepMs = ThreadLocalRandom.current().nextInt(min, max + 1);
    logger.debug("Scraper jitter: sleep {}ms before requesting {}", sleepMs, url);
    sleepQuietly(sleepMs);
  }

  private void applyGlobalThrottleIfEnabled() {
    if (!throttleEnabled) {
      return;
    }

    long interval = Math.max(0, throttleMinIntervalMs);
    if (interval == 0) {
      return;
    }

    while (true) {
      long now = clock.millis();
      long allowedAt = nextAllowedRequestAtMillis.get();
      long desired = Math.max(now, allowedAt);
      long newAllowed = desired + interval;
      if (nextAllowedRequestAtMillis.compareAndSet(allowedAt, newAllowed)) {
        long sleepMs = desired - now;
        if (sleepMs > 0) {
          logger.debug("Scraper throttle: sleep {}ms", sleepMs);
          sleepQuietly(sleepMs);
        }
        return;
      }
    }
  }

  private void applyGlobalCooldownIfNeeded() {
    if (!backoffEnabled) {
      return;
    }
    long now = clock.millis();
    long cooldownUntil = globalCooldownUntilMillis.get();
    if (cooldownUntil > now) {
      long sleepMs = cooldownUntil - now;
      logger.warn("Scraper backoff: global cooldown active, sleep {}ms", sleepMs);
      sleepQuietly(sleepMs);
    }
  }

  private void onRateLimitHit(String url) {
    if (!backoffEnabled) {
      return;
    }

    int hits = consecutiveRateLimitHits.incrementAndGet();
    long base = Math.max(0, backoffBaseDelayMs);
    long max = Math.max(base, backoffMaxDelayMs);

    long exp = base * (1L << Math.min(hits - 1, 10));
    long backoff = Math.min(exp, max);

    // kleiner Jitter auf den Backoff
    long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, base));
    long until = clock.millis() + backoff + jitter;

    globalCooldownUntilMillis.updateAndGet(prev -> Math.max(prev, until));

    logger.warn("Scraper rate-limited (1015). hits={} backoff={}ms (plus jitter={}ms). url={}",
        hits, backoff, jitter, url);

    if (hits >= Math.max(1, maxRateLimitHits)) {
      throw new RateLimitedException("Rate limited too often (hits=" + hits + ") – aborting run");
    }
  }

  private void resetBackoffOnSuccess() {
    if (!backoffEnabled) {
      return;
    }
    consecutiveRateLimitHits.set(0);
  }

  private static boolean isRateLimitIndication(RuntimeException e) {
    if (e instanceof RateLimitedException) {
      return true;
    }
    String msg = e.getMessage();
    if (msg == null) {
      return false;
    }
    String lower = msg.toLowerCase();
    return lower.contains("error 1015")
        || lower.contains("rate limit")
        || lower.contains("too many requests")
        || lower.contains("being rate limited");
  }

  private void sleepQuietly(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private List<Angebot> scrapeOffers(BrowserContext context, String url) {
    Page page = context.newPage();

    try {
      page.navigate(url, new Page.NavigateOptions().setTimeout(30000));

      // Rate-limit Seite wird häufig trotzdem ausgeliefert – wir prüfen Content grob.
      String content = "";
      try {
        content = page.content();
      } catch (Exception ignored) {
        // ignore
      }
      String lowerContent = content.toLowerCase();
      if (lowerContent.contains("error 1015")
          || lowerContent.contains("being rate limited")
          || lowerContent.contains("too many requests")) {
        throw new RateLimitedException("Error 1015: You are being rate limited");
      }

      if (!page.url().contains("cardmarket.com")) {
        throw new RuntimeException("Seite nicht korrekt geladen: " + page.url());
      }
      page.waitForSelector(".table.article-table .article-row",
          new Page.WaitForSelectorOptions().setTimeout(30000));

      List<ElementHandle> rows = page.querySelectorAll(".table.article-table .article-row");
      Map<String, Angebot> angebote = new LinkedHashMap<>();
      int i = 1;
      for (ElementHandle row : rows) {
        ElementHandle nameEl = row.querySelector(".seller-info .seller-name a");
        if (nameEl == null) {
          continue;
        }
        String name = nameEl.innerText().trim();

        ElementHandle preisEl = row.querySelector(".price-container span.color-primary");
        if (preisEl == null) {
          preisEl = row.querySelector(".mobile-offer-container span.color-primary");
        }
        if (preisEl == null) {
          continue;
        }

        String preisText =
            preisEl.innerText().replace(".", "").replace(",", ".").replace("€", "").trim();
        double preis;
        try {
          preis = Double.parseDouble(preisText);
        } catch (NumberFormatException e) {
          continue;
        }

        ElementHandle mengeEl = row.querySelector(".amount-container .item-count");
        String menge = (mengeEl != null) ? mengeEl.innerText().trim() : "—";

        Angebot vorhandenes = angebote.get(name);

        if (vorhandenes == null || preis < vorhandenes.getPreis()) {

          angebote.put(name, new Angebot(name, preis, menge));
          i++;
        }
        if (i > 5) {
          break;
        }
      }
      List<Angebot> angebotList = new ArrayList<>();

      for (Map.Entry<String, Angebot> entry : angebote.entrySet()) {

        Angebot angebot = entry.getValue();
        logger.info(String.format("\nVerkäufer:     %s\nPreis:         %.2f €\nVerfügbarkeit: %s\n\n",
            angebot.getName(), angebot.getPreis(), angebot.getMenge()));
        angebotList.add(angebot);
      }

      return angebotList;
    } finally {
      try {
        page.close();
      } catch (Exception e) {
        logger.debug("Could not close Playwright page", e);
      }
    }

  }

}