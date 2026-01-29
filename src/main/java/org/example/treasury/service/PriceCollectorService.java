package org.example.treasury.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    return fetchOffersWithControls(context, url);
  }

  private List<Angebot> fetchOffersWithControls(BrowserContext context, String url) {
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

    return isErrorMessage(msg.toLowerCase());
  }

  private static boolean isErrorMessage(String msg) {
    String lower = msg.toLowerCase();
    return lower.contains("error 1015")
        || lower.contains("rate limit")
        || lower.contains("too many requests")
        || lower.contains("being rate limited")
        || lower.contains("timeout");
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

      if (isErrorMessage(content)) {
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