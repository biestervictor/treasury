# agents.md — Treasury: Technische Projektdokumentation

## Arbeitsweise & Deployment-Workflow

### Branches
- **`dev`** — Entwicklungsbranch. Hier werden Features entwickelt und gemergt.
- **`main`** — Produktionsbranch. Nur manuell oder auf explizite Aufforderung mergen.

### Lokaler Entwicklungsablauf
1. Feature/Fix auf `dev` implementieren
2. **Lokal bauen:** `mvn clean install` — stellt sicher, dass Checkstyle, Tests und Kompilierung passen
3. **Push auf `dev`:** `git push origin dev`
4. **CI/CD läuft automatisch** (GitHub Actions `.github/workflows/maven.yml`):
   - Patch-Version in `pom.xml` wird automatisch inkrementiert
   - Maven Build + Tests gegen MongoDB 6 Service-Container
   - CodeQL Sicherheitsanalyse
   - Docker Multi-Arch Build (amd64 + arm64) → `ghcr.io/biestervictor/treasury`
   - Helm `values.yaml` (image tag) + `Chart.yaml` (appVersion) werden automatisch aktualisiert
   - Git-Tag `v<version>` wird gesetzt
5. **ArgoCD deployed automatisch** das neue Image aus dem `dev` Branch in die Dev-Umgebung

### Deployment auf Produktion
- Muss **manuell** erfolgen oder auf **explizite Aufforderung**
- Vorgehen: Merge `dev` → `main` (PR oder direkter Merge)
- ArgoCD erkennt den neuen Commit auf `main` und deployed in die Produktionsumgebung (`treasury-kubitos.biester.vip`)

### Checkstyle
- Google Style (`google_checks.xml`) wird bei `mvn validate` erzwungen
- Fehler brechen den Build ab — lokal testen bevor Push!
- 2-Space-Einrückung, Javadoc für alle public-Methoden erforderlich

---

## Inhaltsverzeichnis
1. [Projektübersicht](#1-projektübersicht)
2. [Projektstruktur](#2-projektstruktur)
3. [Technologie-Stack](#3-technologie-stack)
4. [Datenmodelle & Entitäten](#4-datenmodelle--entitäten)
5. [REST-Endpunkte / Controller](#5-rest-endpunkte--controller)
6. [Services](#6-services)
7. [Repository-Schicht](#7-repository-schicht)
8. [Externe Integrationen](#8-externe-integrationen)
9. [MongoDB Collections](#9-mongodb-collections)
10. [Jobs & Scheduling](#10-jobs--scheduling)
11. [CI/CD Pipeline](#11-cicd-pipeline)
12. [Deployment (Docker & Helm)](#12-deployment-docker--helm)
13. [Konfiguration](#13-konfiguration)
14. [Tests](#14-tests)

---

## 1. Projektübersicht

**treasury** ist eine persönliche Asset-Management-Webanwendung (Spring Boot + Thymeleaf + MongoDB) zur Verfolgung von:
- **MTG Displays** – Booster-Boxen mit automatischem Cardmarket-Preis-Scraping
- **Secret Lair Drops** – MTG Sonderprodukte, ebenfalls via Cardmarket-Scraping
- **Edelmetalle** – Gold/Silber mit automatischer Preisverfolgung via goldpreis.de
- **Sneakers** – StockX-Wertverfolgung
- **MagicSets** – Stammdaten synchronisiert von der Scryfall-API

**Kernfunktionen:**
- Playwright/Chromium headless Scraping mit Jitter, Throttling und exponentiellem Backoff (Rate-Limit-Erkennung: Error 1015)
- Edelmetallbewertung mit historischer Gewinn-Timeline (Chart.js)
- Job-Control über eine Settings-UI (Enable/Disable + Cron-Konfiguration zur Laufzeit)
- Mail-Benachrichtigungen via Mailjet SMTP (Startup, Sell-Alerts)
- CSV-Import für initiale Datenbefüllung

**Koordinaten:** `groupId=org.example`, `artifactId=treasury`, aktuelle Version `0.0.3`

---

## 2. Projektstruktur

```
src/main/java/org/example/treasury/
├── TreasuryApplication.java          # @SpringBootApplication + @EnableScheduling + @EnableAsync
├── config/
│   ├── JobSettingsProperties.java    # @ConfigurationProperties(prefix="treasury.jobs")
│   └── MailProperties.java          # @ConfigurationProperties(prefix="treasury.mail")
├── controller/
│   ├── DisplayController.java        # /api/display
│   ├── EdelmetallController.java     # /api/edelmetall
│   ├── MenueController.java          # /api/menue
│   ├── NavStatusAdvice.java          # @ControllerAdvice – globale Model-Attribute
│   ├── SecretLairController.java     # /api/secretlair
│   ├── SetCollectionController.java  # /api/sets
│   ├── SettingsController.java       # /api/settings
│   └── ShoeController.java          # /api/shoe
├── dto/
│   ├── JobSettingsUpdateRequest.java
│   ├── MailRequest.java
│   ├── ManualMetalPricesRequest.java
│   └── MetalDashboardDto.java        # record mit nested PriceDto, ProfitPointDto, MarketValuePointDto
├── job/
│   ├── MagicSetJob.java              # Scryfall-Set-Sync
│   ├── MetalPriceScraperJob.java     # Edelmetallpreis-Scraping
│   ├── PriceScraperJob.java          # Cardmarket-Preisscraping (Displays + SecretLair)
│   └── SellJob.java                 # Sell-Monitoring (nur isSelling=true)
├── model/
│   ├── AggregatedDisplay.java        # View-Aggregation (kein @Document)
│   ├── Angebot.java                  # Eingebettetes Cardmarket-Angebot
│   ├── CardMarketModel.java          # Abstrakte Basisklasse für Display + SecretLair
│   ├── Display.java                  # @Document(collection="displays")
│   ├── DisplayType.java              # enum: COLLECTOR, DRAFT, PLAY, BUNDLE, GIFTBOX, PRERELEASE, SET
│   ├── JobKey.java                   # enum: SELL, PRICE_SCRAPER, MAGIC_SET, METAL_PRICE_SCRAPER
│   ├── JobRuntimeSettings.java       # record
│   ├── JobSchedule.java
│   ├── JobSetting.java               # record
│   ├── MagicSet.java                 # @Id = code
│   ├── MetalPriceSnapshot.java       # @Document(collection="metalPriceSnapshot")
│   ├── MetalValuationSnapshot.java   # @Document(collection="metalValuationSnapshot")
│   ├── PreciousMetal.java            # @Document(collection="preciousMetal")
│   ├── PreciousMetalType.java        # enum: GOLD, SILVER
│   ├── SecretLair.java               # @Document(collection="secretLair")
│   ├── SetType.java                  # enum: DRAFT, MASTERS, FUNNY, EXPANSION, CORE, DRAFT_INNOVATION
│   └── Shoe.java                    # @Document(collection="shoe")
├── persistence/
│   └── JobRuntimeSettingsEntity.java # @Document(collection="job_runtime_settings")
├── repository/
│   ├── DisplayRepository.java
│   ├── JobRuntimeSettingsRepository.java
│   ├── MagicSetRepository.java
│   ├── MetalPriceSnapshotRepository.java
│   ├── MetalValuationSnapshotRepository.java
│   ├── PreciousMetalRepository.java
│   ├── SecretLairRepository.java
│   └── ShoeRepository.java
└── service/
    ├── CsvImporter.java
    ├── DisplayPriceCollectorService.java
    ├── DisplayService.java
    ├── EdelmetallCsvParser.java
    ├── EdelmetallService.java
    ├── GoldpreisDeMetalPriceClient.java
    ├── JobRuntimeSettingsService.java
    ├── JobScheduleService.java
    ├── JobSettingsService.java
    ├── JobSettingsViewService.java
    ├── JobTriggerService.java
    ├── MagicSetService.java
    ├── MailService.java
    ├── MetalPriceClient.java          # Interface
    ├── PriceCollectorService.java     # Abstrakte Scraping-Basisklasse
    ├── RateLimitedException.java
    ├── ScryFallWebservice.java
    ├── SecretLairPriceCollectorService.java
    ├── SecretLairService.java
    ├── SetCollectionService.java
    ├── ShoeService.java
    └── StartupMailNotifier.java

src/main/resources/
├── application.properties            # Lokal/Default
├── application-ci.properties         # GitHub Actions CI
├── application-docker.properties     # Docker (host.docker.internal)
├── application-kubitos.properties    # Kubernetes (Produktion)
├── Displays.csv                      # Initialdaten MTG-Displays
├── Edelmetalle.csv                   # Initialdaten Edelmetalle
├── Schuhe.csv                        # Initialdaten Schuhe
├── SecretLair.csv                    # Initialdaten SecretLair
├── static/css/treasury.css
└── templates/
    ├── addDisplay.html
    ├── addSecretLair.html
    ├── aggregatedDisplays.html
    ├── display.html
    ├── displayMenue.html
    ├── edelmetallDashboard.html
    ├── index.html
    ├── navbarDisplay.html
    ├── navbarEdelmetall.html
    ├── secretlair.html
    ├── secretLairNavbar.html
    ├── setCollection.html
    ├── settings.html
    ├── shoe.html
    ├── shoeMenue.html
    └── fragments/
        ├── badges.html
        ├── buildDbBadges.html
        ├── layout.html
        ├── scripts.html
        ├── tableControls.html
        ├── tableFilters.html
        ├── theadFilters.html
        └── theadHeaders.html
```

---

## 3. Technologie-Stack

| Kategorie | Technologie | Version |
|---|---|---|
| Sprache | Java | 21 |
| Build | Maven (mvnw) | 3.9.4 |
| Framework | Spring Boot | 4.0.4 |
| Template-Engine | Thymeleaf | (via Parent) |
| Datenbank | MongoDB + Spring Data (sync + reactive) | (via Parent) |
| Browser-Automatisierung | Microsoft Playwright | 1.58.0 |
| HTML-Parsing | Jsoup | 1.22.1 |
| JSON | org.json | 20251224 |
| JSON-Mapping | Jackson Databind + JSR310 | (via Parent) |
| Mail | Spring Boot Starter Mail | (via Parent) |
| Code-Generierung | Lombok | (via Parent) |
| Stil-Prüfung | Checkstyle (Google Style) | 13.3.0 |
| Containerisierung | Docker Multi-Stage Build | – |
| Build-Image | maven:3.9.4-eclipse-temurin-21 | – |
| Runtime-Image | eclipse-temurin:21-jre | – |
| Orchestrierung | Kubernetes + Helm | Chart v0.1.0 |
| Ingress | NGINX | – |
| Secrets | Azure Key Vault + External Secrets Operator | – |
| Mail-Provider | Mailjet SMTP (Port 587, STARTTLS) | – |
| Test | JUnit 5 + AssertJ + Mockito | (via Parent) |
| Mail-Tests | GreenMail JUnit 5 | 2.1.8 |
| Dependency-Updates | GitHub Dependabot (täglich) | – |
| SAST | GitHub CodeQL (Java) | – |
| Container-Registry | GitHub Container Registry (ghcr.io) | – |

---

## 4. Datenmodelle & Entitäten

### 4.1 `CardMarketModel` (abstrakt)
**Datei:** `model/CardMarketModel.java` | Annotationen: `@AllArgsConstructor`, `@NoArgsConstructor`, `@Getter`, `@Setter`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `String` | `@Id` MongoDB-ID |
| `updatedAt` | `LocalDate` | Letztes Update |
| `currentValue` | `double` | Aktueller Marktwert (Scraping) |
| `url` | `String` | Cardmarket-URL (default: `""`) |
| `valueBought` | `double` | Einkaufspreis |
| `dateBought` | `LocalDate` | Kaufdatum |
| `name` | `String` | Bezeichnung |
| `isSold` | `boolean` | Verkauft? |
| `soldPrice` | `double` | Verkaufspreis |
| `location` | `String` | Aufbewahrungsort (default: `""`) |
| `language` | `String` | Sprache (default: `"EN"`) |
| `isSelling` | `boolean` | Auf Cardmarket inseriert? (default: `false`) |
| `angebotList` | `List<Angebot>` | Günstigste Angebote vom Scraping |

**Methode:** `getRelevantPreis()` — Outlier-Filter: wenn günstigstes < 85% des zweiten, wähle das zweite.

---

### 4.2 `Display`
**Datei:** `model/Display.java` | `@Document(collection="displays")` | erbt `CardMarketModel`

| Feld | Typ | Beschreibung |
|---|---|---|
| `setCode` | `String` | MTG Set-Code (z.B. `"znr"`) |
| `type` | `String` | COLLECTOR / DRAFT / PLAY / SET / BUNDLE / PRERELEASE |
| `vendor` | `String` | Händler (aus CSV) |

---

### 4.3 `SecretLair`
**Datei:** `model/SecretLair.java` | `@Document(collection="secretLair")` | erbt `CardMarketModel`

| Feld | Typ | Beschreibung |
|---|---|---|
| `isDeck` | `boolean` | Commander Deck? |
| `isFoil` | `boolean` | Foil-Version? |

---

### 4.4 `Angebot` (eingebettet)
**Datei:** `model/Angebot.java`

| Feld | Typ | Beschreibung |
|---|---|---|
| `name` | `String` | Verkäufer-Name |
| `preis` | `double` | Preis in EUR |
| `menge` | `String` | Verfügbare Menge |

---

### 4.5 `MagicSet`
**Datei:** `model/MagicSet.java` | Annotationen: `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Getter`, `@Setter`

| Feld | Typ | Beschreibung |
|---|---|---|
| `code` | `String` | `@Id` Set-Code |
| `name` | `String` | Set-Name |
| `uri` | `String` | Scryfall-URI |
| `iconUri` | `String` | Set-Icon SVG URI |
| `setType` | `String` | Typ (draft, expansion, masters, ...) |
| `releaseDate` | `LocalDate` | Erscheinungsdatum |
| `cardCount` | `int` | Anzahl Karten |

---

### 4.6 `PreciousMetal`
**Datei:** `model/PreciousMetal.java` | `@Document(collection="preciousMetal")` | `@Builder`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `String` | `@Id` |
| `importKey` | `String` | Deterministischer Dedup-Key: `name\|year\|qty\|weight\|type\|price` |
| `name` | `String` | Bezeichnung (z.B. "Gold Maple Leaf") |
| `year` | `Integer` | Erscheinungsjahr (optional) |
| `weightInGrams` | `double` | Gewicht in Gramm |
| `quantity` | `int` | Anzahl |
| `type` | `PreciousMetalType` | GOLD oder SILVER |
| `purchasePrice` | `double` | Einkaufspreis pro Einheit (EUR) |
| `importedAt` | `LocalDate` | Import-Datum |

---

### 4.7 `MetalPriceSnapshot`
**Datei:** `model/MetalPriceSnapshot.java` | `@Document(collection="metalPriceSnapshot")` | `@Builder`

| Feld | Typ |
|---|---|
| `id` | `String` |
| `timestamp` | `Instant` |
| `goldPriceEurPerOunce` | `double` |
| `silverPriceEurPerOunce` | `double` |

---

### 4.8 `MetalValuationSnapshot`
**Datei:** `model/MetalValuationSnapshot.java` | `@Document(collection="metalValuationSnapshot")` | `@Builder`

| Feld | Typ |
|---|---|
| `id` | `String` |
| `timestamp` | `Instant` |
| `items` | `List<ItemValuation>` |
| `totalCurrentValue` | `double` |
| `totalProfit` | `double` |

**Nested Record `ItemValuation`:**

| Feld | Typ |
|---|---|
| `preciousMetalId` | `String` |
| `name` | `String` |
| `type` | `PreciousMetalType` |
| `weightInGrams` | `double` |
| `quantity` | `int` |
| `priceEurPerOunce` | `double` |
| `currentUnitValue` | `double` |
| `currentTotalValue` | `double` |
| `purchasePrice` | `double` |
| `profit` | `double` |

---

### 4.9 `Shoe`
**Datei:** `model/Shoe.java` | `@Document(collection="shoe")`

| Feld | Typ |
|---|---|
| `id` | `String` |
| `name` | `String` |
| `typ` | `String` |
| `usSize` | `String` |
| `dateBought` | `Date` |
| `valueBought` | `double` |
| `valueStockX` | `double` |
| `winStockX` | `double` |
| `updatedAt` | `Date` |
| `valueSold` | `double` |

---

### 4.10 `JobRuntimeSettingsEntity`
**Datei:** `persistence/JobRuntimeSettingsEntity.java` | `@Document(collection="job_runtime_settings")`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `String` | `@Id` |
| `key` | `String` | JobKey-Name (z.B. `"SELL"`) |
| `enabled` | `boolean` | Aktiviert? |
| `cron` | `String` | Spring-Cron (6 Felder inkl. Sekunde) |
| `zoneId` | `String` | Zeitzone (null = System-Default) |
| `updatedAt` | `Instant` | Letztes Update |

---

### 4.11 Enums

| Enum | Datei | Werte |
|---|---|---|
| `DisplayType` | `model/DisplayType.java` | COLLECTOR, DRAFT, PLAY, BUNDLE, GIFTBOX, PRERELEASE, SET |
| `PreciousMetalType` | `model/PreciousMetalType.java` | GOLD, SILVER |
| `JobKey` | `model/JobKey.java` | SELL, PRICE_SCRAPER, MAGIC_SET, METAL_PRICE_SCRAPER |
| `SetType` | `model/SetType.java` | DRAFT, MASTERS, FUNNY, EXPANSION, CORE, DRAFT_INNOVATION |

---

### 4.12 DTOs (Records)

| DTO | Felder |
|---|---|
| `MetalDashboardDto` | `currentPrices`, `profitTimeline`, `latestValuations`, `marketValueTimeline`, `currentProfitTotal`, `currentMarketValueTotal` |
| `MetalDashboardDto.PriceDto` | `goldEurPerOunce`, `silverEurPerOunce`, `timestamp` |
| `MetalDashboardDto.ProfitPointDto` | `timestamp`, `profitTotal` |
| `MetalDashboardDto.MarketValuePointDto` | `timestamp`, `marketValueTotal` |
| `ManualMetalPricesRequest` | `goldEurPerOunce (String)`, `silverEurPerOunce (String)` |
| `MailRequest` | `to (List<String>)`, `subject`, `text` |
| `JobSettingsUpdateRequest` | `sellEnabled`, `priceScraperEnabled`, `magicSetEnabled`, `metalPriceScraperEnabled` |
| `JobSetting` | `key (JobKey)`, `displayName`, `enabled`, `schedule`, `lastTriggeredAt`, `updatedAt` |
| `JobRuntimeSettings` | `key (JobKey)`, `enabled`, `cron`, `zoneId`, `updatedAt` |

---

## 5. REST-Endpunkte / Controller

### 5.1 `DisplayController` — `@RequestMapping("/api/display")`
**Datei:** `controller/DisplayController.java`

| HTTP | Pfad | Methode | Beschreibung |
|---|---|---|---|
| GET | `/api/display/new` | `addDisplay(Model)` | Formular für neues Display |
| POST | `/api/display/save` | `saveDisplay(@ModelAttribute Display)` | Display speichern → Redirect `/list` |
| GET | `/api/display/insert` | `insertDisplays(Model)` | CSV-Import + Anzeige |
| GET | `/api/display/aggregated` | `getAggregatedDisplays(Model)` | Aggregierte Ansicht nach SetCode+Type |
| GET | `/api/display` | `getAllDisplays()` | Alle Displays (JSON) |
| GET | `/api/display/{id}` | `getDisplayById(@PathVariable)` | Display by ID (JSON) |
| POST | `/api/display` | `createDisplay(@RequestBody)` | Neues Display anlegen (JSON) |
| PUT | `/api/display/{id}` | `updateDisplay(@PathVariable, @RequestBody)` | Display updaten (JSON) |
| DELETE | `/api/display/{id}` | `deleteDisplay(@PathVariable)` | Display löschen |
| GET | `/api/display/setCode/{setCode}` | `getDisplaysBySetCode` | Filter by SetCode (JSON) |
| GET | `/api/display/type/{type}` | `getDisplaysByType` | Filter by Type (JSON) |
| GET | `/api/display/valueRange` | `getDisplaysByValueRange` | Filter by Preisspanne (JSON) |
| GET | `/api/display/list` | `getList(setCode?, type?, soldOnly?, isSelling?, highProfitOnly?)` | Gefilterte Liste mit Summen (View) |
| POST | `/api/display/update` | `updateDisplay(@ModelAttribute)` | Display aus Formular updaten |

---

### 5.2 `EdelmetallController` — `@RequestMapping("/api/edelmetall")`
**Datei:** `controller/EdelmetallController.java`

| HTTP | Pfad | Methode | Beschreibung |
|---|---|---|---|
| POST | `/api/edelmetall/import` | `importCsv()` | CSV-Import starten (`@ResponseBody`) |
| POST | `/api/edelmetall/prices/update` | `updatePrices()` | Automatischer Preisabruf + Snapshot → Redirect |
| POST | `/api/edelmetall/prices/manual` | `manualPrices(@RequestBody ManualMetalPricesRequest)` | Manuelle Preiseingabe (JSON API) |
| POST | `/api/edelmetall/prices/manual/view` | `manualPricesView(@RequestBody)` | Manuelle Preiseingabe (Formular) → Redirect |
| GET | `/api/edelmetall/dashboard` | `dashboard()` | Dashboard-Daten als JSON |
| GET | `/api/edelmetall/dashboard/view` | `dashboardView(Model)` | Thymeleaf-Dashboard-View |

---

### 5.3 `SecretLairController` — `@RequestMapping("/api/secretlair")`
**Datei:** `controller/SecretLairController.java`

| HTTP | Pfad | Methode | Beschreibung |
|---|---|---|---|
| POST | `/api/secretlair/add` | `addSecretLair(@ModelAttribute)` | Neuer SecretLair → Redirect |
| GET | `/api/secretlair/insert` | `insertSecretLair(soldOnly?, isSelling?, highProfitOnly?)` | Listenansicht + Filter |
| POST | `/api/secretlair/update` | `updateSecretLair(id, location, currentValue, isSold, selling, soldPrice, dateBought)` | Daten bearbeiten |

---

### 5.4 `ShoeController` — `@RequestMapping("/api/shoe")`
**Datei:** `controller/ShoeController.java`

| HTTP | Pfad | Methode | Beschreibung |
|---|---|---|---|
| GET | `/api/shoe/insert` | `insertShoes(Model)` | CSV-Import + Anzeige |
| POST | `/api/shoe/updateValueSold` | `updateValueSold(id, valueSold)` | Verkaufspreis setzen → Redirect |
| GET | `/api/shoe/list` | `getList(Model)` | Listenansicht mit Summen |

---

### 5.5 `SettingsController` — `@RequestMapping("/api/settings")`
**Datei:** `controller/SettingsController.java`

| HTTP | Pfad | Methode | Beschreibung |
|---|---|---|---|
| GET | `/api/settings` | `settings(Model)` | Job-Einstellungen + NextRun |
| POST | `/api/settings/jobs` | `updateJobs(sellEnabled, priceScraperEnabled, magicSetEnabled, metalPriceScraperEnabled, *Cron, triggerJob?)` | Jobs konfigurieren + manuell triggern |

---

### 5.6 `SetCollectionController` — `@RequestMapping("/api/sets")`
**Datei:** `controller/SetCollectionController.java`

| HTTP | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/sets/list` | Alle Sets + fehlende Sets (ohne Display) |
| GET | `/api/sets/filter` | Gefiltert nach Komma-separiertem setType |

---

### 5.7 `MenueController` — `@RequestMapping("/api/menue")`
**Datei:** `controller/MenueController.java`

| HTTP | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/menue/index` | Startseite |
| GET | `/api/menue/shoeMenue` | Redirect → `/api/shoe/list` |
| GET | `/api/menue/displayMenue` | View: `displayMenue` |

---

### 5.8 `NavStatusAdvice` (`@ControllerAdvice`)
**Datei:** `controller/NavStatusAdvice.java`

Stellt folgende globale `@ModelAttribute` für **alle** Views bereit:

| Attribut | Typ | Beschreibung |
|---|---|---|
| `jobsAllEnabled` | `boolean` | true wenn alle 4 Jobs aktiv |
| `jobsAnyDisabled` | `boolean` | true wenn mind. 1 Job deaktiviert |
| `buildVersion` | `String` | `${treasury.build.version}` |
| `buildTime` | `String` | `${treasury.build.time}` |
| `mongodbUri` | `String` | Aktive MongoDB-URI |
| `dbIsDev` | `boolean` | true wenn In-Cluster Kubernetes MongoDB |
| `dbIsProd` | `boolean` | true wenn externe MongoDB |

---

## 6. Services

### 6.1 `DisplayService`
**Datei:** `service/DisplayService.java`

| Methode | Beschreibung |
|---|---|
| `getAllDisplays()` | Alle Displays |
| `getDisplayById(id)` | By ID |
| `saveDisplay(display)` | Einzeln speichern |
| `saveAllDisplays(displays)` | Bulk |
| `deleteDisplay(id)` | Löschen |
| `findBySetCodeIgnoreCase(setCode)` | Case-insensitiv |
| `getDisplaysByType(type)` | Filter by Typ |
| `getDisplaysByValueRange(min, max)` | Filter by Preis |
| `findBySetCodeAndType(setCode, type)` | Kombinierter Filter |
| `getAggregatedValues()` | Map: SetCode → Type → {count, totalValue, avgPrice, relevantPreis} |
| `getAggregatedTotals()` | Record `AggregatedTotals(totalExpenses, currentValue)` nur nicht-verkaufte |
| `updateDisplayById(id, updatedDisplay)` | Partielles Update (location, sold, soldPrice, selling, language) |
| `updateAngeboteBySetCodeAndType(displayNew)` | Aktualisiert Angebote + URL aller Displays gleichen SetCodes+Typs |

---

### 6.2 `EdelmetallService`
**Datei:** `service/EdelmetallService.java`

**Konstanten:**
- `DEFAULT_GOLD_PRICE_PER_OUNCE = 3864.38`
- `DEFAULT_SILVER_PRICE_PER_OUNCE = 58.83`
- `GRAMS_PER_TROY_OUNCE = 31.1034768`

| Methode | Beschreibung |
|---|---|
| `importEdelmetallFromCSV()` | Liest `Edelmetalle.csv`, importiert idempotent via `importKey`, erstellt initialen Snapshot |
| `importMetals(metals, importedAt)` | Import mit Duplikat-Check via `importKey` |
| `updatePrices()` | Ruft `MetalPriceClient.fetchCurrentPrices()` → `MetalPriceSnapshot` speichern |
| `updatePricesAndStoreValuation()` | `updatePrices()` + `storeValuationSnapshot()` |
| `storeManualPricesAndValuation(request)` | Manuelle EUR-Preise parsen + Snapshot ohne externen API-Call |
| `getDashboard()` | Baut `MetalDashboardDto` mit Profit-Timeline + MarketValue-Timeline |
| `buildImportKey(metal)` | Statisch: `name\|year\|qty\|weight\|type\|price` |
| `parseEuroNumber(input)` | Statisch: DE-Format `"3.864,38"` → `3864.38` |

---

### 6.3 `PriceCollectorService` (abstrakt)
**Datei:** `service/PriceCollectorService.java`

Scraping-Infrastruktur mit konfigurierbaren Schutzmaßnahmen:

| Feature | Properties-Prefix | Beschreibung |
|---|---|---|
| Jitter | `treasury.scraper.jitter.*` | Zufällige Pause 750–2500ms (lokal), 30–300s (Prod) |
| Throttle | `treasury.scraper.throttle.*` | Globaler Mindestabstand 2000ms (lokal), 20000ms (Prod); thread-sicher via `AtomicLong` |
| Backoff | `treasury.scraper.backoff.*` | Exponentieller Backoff bei Rate-Limit; Start 15s, Max 300s, Max 3 Hits → `RateLimitedException` |

**Rate-Limit-Erkennung:** Error 1015, "rate limit", "too many requests", "timeout"

**`scrapeOffers(context, url)`:** Navigiert zu URL, wartet auf `.table.article-table .article-row`, extrahiert bis zu 5 günstigste Angebote.

---

### 6.4 `DisplayPriceCollectorService`
**Datei:** `service/DisplayPriceCollectorService.java` | erbt `PriceCollectorService`

| Methode | Beschreibung |
|---|---|
| `runScraper(playwright, display, isLegacy)` | Scrapt Cardmarket-Preise; bei `isSelling=true` + Unterbietung → Mail-Alert |
| `buildUrl(setCode, setName, type, isLegacy, language)` | Baut Cardmarket-URL (package-private) |
| `fixUrls(setCode, type, url, query)` | Hardcoded Overrides für: 2XM, FIF, M21, THB, MAT, DGM, M20, UNF, CMB2, MB2, WHO, FDN, SPM, TLA, TMT, ACR, BRO+PRERELEASE |

**URL-Schema nach Typ:**
- COLLECTOR → `-Collector-Booster-Box`
- SET → `-Set-Booster-Box`
- PLAY → `-Play-Booster-Box`
- DRAFT → `-Draft-Booster-Box`
- BUNDLE → `Products/Bundles-Fat-Packs/...-Fat-Pack-Bundle`
- PRERELEASE → `Products/Tournament-Packs/...-Prerelease-Pack`

---

### 6.5 `SecretLairPriceCollectorService`
**Datei:** `service/SecretLairPriceCollectorService.java` | erbt `PriceCollectorService`

| Methode | Beschreibung |
|---|---|
| `runScraper(playwright, secretLairList)` | Scrapt alle SecretLairs |
| `buildUrl(secretLair)` | `isDeck=true` → `Preconstructed-Decks`, sonst `Sets/Secret-Lair-Drop-Series-` |
| `fixUrls(name, url)` | Overrides für: Pride, Barcelona Rats, Chicago (Serra/Sliver/Ponder), Vegas (Rats/Sliver/Ponder), Scarab, Ways, Creative, Dead Eye, Sol |

---

### 6.6 `GoldpreisDeMetalPriceClient`
**Datei:** `service/GoldpreisDeMetalPriceClient.java` | implementiert `MetalPriceClient`

Scrapt `https://www.goldpreis.de` (Gold) und `https://www.goldpreis.de/silberpreis/` (Silber) via Jsoup.
Regex: `(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})` | Timeout: 15000ms

---

### 6.7 `ScryFallWebservice`
**Datei:** `service/ScryFallWebservice.java`

`getSetList()` — Holt alle MTG-Sets von `https://api.scryfall.com/sets`, paginiert (`has_more`/`next_page`).
Filter: kein Digital, cardCount > 0, SetType in `SetType`-Enum (oder code = "who").

---

### 6.8 `MailService`
**Datei:** `service/MailService.java`
`@ConditionalOnProperty(prefix="treasury.mail", name="enabled", havingValue="true")`

`send(MailRequest)` — Plaintext-Mail via JavaMailSender. From + Reply-To aus `MailProperties`.

---

### 6.9 `StartupMailNotifier`
**Datei:** `service/StartupMailNotifier.java`
`@EventListener(ApplicationReadyEvent.class)` — Startup-Mail wenn `treasury.mail.startup.enabled=true`.

---

### 6.10 `CsvImporter`
**Datei:** `service/CsvImporter.java`

| Methode | Datei | CSV-Format |
|---|---|---|
| `importCsv(filePath)` | `Schuhe.csv` | `;`-separiert → `List<Shoe>` |
| `importDisplayCsv(filePath)` | `Displays.csv` | `SetCode-Typ;Einkaufspreis;Händler` (D=DRAFT, P=PRERELEASE, C=COLLECTOR, S=SET, F=BUNDLE) |
| `importSecretLairCsv(filePath)` | `SecretLair.csv` | `,`-separiert: Name, Menge, Normal, Foil, Einzelpreis |

**Besonderheit:** Ab MKM-Releasedate DRAFT → PLAY automatisch.

---

### 6.11 Job-Services

| Service | Datei | Beschreibung |
|---|---|---|
| `JobSettingsService` | `service/JobSettingsService.java` | In-Memory + MongoDB: Job-Flags (enabled/disabled) |
| `JobRuntimeSettingsService` | `service/JobRuntimeSettingsService.java` | `AtomicReference<EnumMap>` + MongoDB: cron + enabled pro JobKey |
| `JobTriggerService` | `service/JobTriggerService.java` | `@Async` manuelles Triggern per JobKey |
| `JobScheduleService` | `service/JobScheduleService.java` | `nextRun(JobSchedule, Instant)` via `CronExpression.parse()` |
| `JobSettingsViewService` | `service/JobSettingsViewService.java` | Baut `List<JobSetting>` für Settings-UI |

---

### 6.12 Domain-Services (einfach)

| Service | Datei | Kernmethoden |
|---|---|---|
| `MagicSetService` | `service/MagicSetService.java` | `getAllMagicSets()`, `getMagicSetByCode(code)`, `saveAllMagicSets(sets)` |
| `SecretLairService` | `service/SecretLairService.java` | `addSecretLair()`, `saveAllSecretLairs()`, `getAllSecretLairs()`, `updateSecretLair(...)` (3 Overloads) |
| `ShoeService` | `service/ShoeService.java` | `getAllShoes()`, `saveAllShoes()`, `updateValueSold(id, valueSold)` |
| `SetCollectionService` | `service/SetCollectionService.java` | `getMissingSets()` — Sets ohne Displays (filtert: Time Spiral, funny, core, draft_innovation, masters) |

---

## 7. Repository-Schicht

Alle Repositories erweitern `MongoRepository<T, String>`.

| Repository | Entity | Collection | Zusatz-Methoden |
|---|---|---|---|
| `DisplayRepository` | `Display` | `displays` | `findByType`, `findByValueBoughtBetween`, `findBySetCodeIgnoreCase (@Query)`, `findBySetCodeIgnoreCaseAndTypeIgnoreCase`, `findByTypeIgnoreCase` |
| `SecretLairRepository` | `SecretLair` | `secretLair` | Standard-CRUD |
| `ShoeRepository` | `Shoe` | `shoe` | `findById(shoeId)` |
| `MagicSetRepository` | `MagicSet` | *(default)* | `findAllByCode(code)` |
| `PreciousMetalRepository` | `PreciousMetal` | `preciousMetal` | `findAllByImportedAtBetween`, `findAllByImportedAtIsNotNull`, `findByImportKey`, `existsByImportKey` |
| `MetalPriceSnapshotRepository` | `MetalPriceSnapshot` | `metalPriceSnapshot` | `findTopByOrderByTimestampDesc()`, `deleteByTimestampBefore(timestamp)` |
| `MetalValuationSnapshotRepository` | `MetalValuationSnapshot` | `metalValuationSnapshot` | `findTopByOrderByTimestampDesc()`, `findAllByOrderByTimestampAsc()`, `deleteByTimestampBefore(timestamp)` |
| `JobRuntimeSettingsRepository` | `JobRuntimeSettingsEntity` | `job_runtime_settings` | `findByKey(key)` |

---

## 8. Externe Integrationen

### 8.1 Cardmarket-Scraping (Playwright)
- **Technologie:** Microsoft Playwright 1.58.0, Chromium headless
- **User-Agent:** `Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36`
- **Basis-URL:** `https://www.cardmarket.com/de/Magic/Products/Booster-Boxes/...`
- **CSS-Selektoren:** `.table.article-table .article-row`, `.seller-info .seller-name a`, `.price-container span.color-primary`, `.amount-container .item-count`
- **Schutzmaßnahmen:** Jitter + Throttle + Exponentieller Backoff (siehe `PriceCollectorService`)

### 8.2 goldpreis.de-Scraping (Jsoup)
- **URLs:** `https://www.goldpreis.de` | `https://www.goldpreis.de/silberpreis/`
- **User-Agent:** `Mozilla/5.0 (compatible; treasury-bot/1.0)`
- **Timeout:** 15000ms

### 8.3 Scryfall API
- **URL:** `https://api.scryfall.com/sets`
- **Authentifizierung:** Keine (öffentliche API)
- **Paginierung:** `has_more` + `next_page`

### 8.4 Mailjet SMTP
- **Host:** `in-v3.mailjet.com` (via `MAILJET_SMTP_HOST`)
- **Port:** 587, STARTTLS
- **Verwendung:** Startup-Mail, Sell-Alert bei Unterbietung

---

## 9. MongoDB Collections

**Datenbank:** `treasury`  
**Lokal:** `mongodb://localhost:27017/treasury`  
**Kubernetes:** via `SPRING_MONGODB_URI` aus ExternalSecret (externe MongoDB: `192.168.178.141:27017`)

| Collection | Typ | Beschreibung |
|---|---|---|
| `displays` | `Display` | MTG Booster-Box Inventar mit eingebetteter `angebotList` |
| `secretLair` | `SecretLair` | Secret Lair Drop Inventar mit eingebetteter `angebotList` |
| `shoe` | `Shoe` | Sneaker-Inventar |
| `preciousMetal` | `PreciousMetal` | Edelmetall-Positionen, Dedup via `importKey` |
| `metalPriceSnapshot` | `MetalPriceSnapshot` | Zeitreihe Gold-/Silberpreise (EUR/oz) |
| `metalValuationSnapshot` | `MetalValuationSnapshot` | Zeitreihe Bewertungen mit embedded `ItemValuation`-Liste |
| `job_runtime_settings` | `JobRuntimeSettingsEntity` | Persistierte Job-Konfiguration |
| *(default MagicSet)* | `MagicSet` | MTG Set-Stammdaten (code als `@Id`) |

---

## 10. Jobs & Scheduling

`@EnableScheduling` + `@EnableAsync` in `TreasuryApplication`

| Job-Klasse | JobKey | Default-Cron | Beschreibung |
|---|---|---|---|
| `PriceScraperJob` | `PRICE_SCRAPER` | Konfigurierbar | Cardmarket-Scraping für alle Displays + SecretLairs |
| `SellJob` | `SELL` | Konfigurierbar | Nur Displays/SecretLairs mit `isSelling=true`; Mail bei Unterbietung |
| `MagicSetJob` | `MAGIC_SET` | Konfigurierbar | Scryfall-Sync aller Sets → MongoDB |
| `MetalPriceScraperJob` | `METAL_PRICE_SCRAPER` | Konfigurierbar | goldpreis.de-Scraping + Valuation-Snapshot |

**Job-Steuerung:**
- Cron und enabled-Flag werden in `job_runtime_settings` Collection persistiert
- Zur Laufzeit änderbar über `/api/settings`
- Manuelles Triggern einzelner Jobs über Settings-UI (`triggerJob=<JobKey>`)

---

## 11. CI/CD Pipeline

**Workflow:** `.github/workflows/maven.yml`  
**Trigger:** Push oder PR auf `dev` (außer `helmcharts/**`)

| # | Schritt | Detail |
|---|---|---|
| 1 | Checkout | `fetch-depth: 0` |
| 2 | JDK 21 Setup | Temurin + Maven-Cache |
| 3 | CodeQL Init | Java |
| 4 | Version Bump | Patch-Increment in `pom.xml` (nur nicht-Dependabot-Pushes) |
| 5 | Maven Build | `mvn clean install -Dspring.profiles.active=ci` + MongoDB 6 Service-Container |
| 6 | CodeQL Analyse | SARIF-Report |
| 7 | QEMU + Buildx | Multi-Arch (amd64 + arm64) |
| 8 | GHCR Login | `ACTION_GITHUB_TOKEN` |
| 9 | Docker Build+Push | `ghcr.io/biestervictor/treasury:latest`, `:<sha>`, `:<version>` |
| 10 | Helm Update | `values.yaml` tag + `Chart.yaml` appVersion per `sed` |
| 11 | Git Commit+Push | `pom.xml`, `values.yaml`, `Chart.yaml` → `dev` mit `[skip ci]` |
| 12 | Git Tag | `v<version>` |

**CI-Services:** `mongo:6` auf Port 27017

---

## 12. Deployment (Docker & Helm)

### Dockerfile (Multi-Stage)
```
Stage 1 (build): maven:3.9.4-eclipse-temurin-21
  → Playwright Browser-Dependencies installieren
  → mvn clean package -DskipTests

Stage 2 (runtime): eclipse-temurin:21-jre
  → System-Libraries für Playwright/Chromium (libglib2.0, libnss3, libatk, etc.)
  → COPY app.jar
  → ENV SPRING_PROFILES_ACTIVE=docker
  → EXPOSE 30800
  → ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Helm-Chart (`helmcharts/`)
- **Chart:** Version `0.1.0`, AppVersion `0.0.3`
- **Namespace:** `treasury`
- **Replicas:** 1
- **Service:** Port 80 → containerPort 8080
- **Ingress:** NGINX, TLS auf `treasury-kubitos.biester.vip`
- **MongoDB:** Extern auf `192.168.178.141:27017` (URI via ExternalSecret)
- **ExternalSecrets:** TLS, Mailjet, Registry-Credentials (alle aus Azure Key Vault `treasurykeyvault.vault.azure.net`)
- **MongoDB StatefulSet:** `mongodb.enabled=false` (Prod nutzt externe DB)
- **Hedgedoc:** `hedgedoc.enabled=false`

**Env-Vars im Pod:**
- `MAILJET_API_KEY` / `MAILJET_API_SECRET` — aus Secret
- `TZ=Europe/Berlin`
- `TREASURY_BUILD_VERSION` / `TREASURY_BUILD_TIME` — aus Values
- `SPRING_MONGODB_URI` — aus ExternalSecret

---

## 13. Konfiguration

### application.properties (lokal)
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/treasury

# Mail
treasury.mail.enabled=false
treasury.mail.startup.enabled=false

# Scraper
treasury.scraper.jitter.min-ms=750
treasury.scraper.jitter.max-ms=2500
treasury.scraper.throttle.min-interval-ms=2000
treasury.scraper.backoff.initial-delay-seconds=15
treasury.scraper.backoff.max-delay-seconds=300
treasury.scraper.backoff.max-hits=3
```

### application-kubitos.properties (Produktion)
```properties
# MongoDB via SPRING_MONGODB_URI Env-Var
spring.data.mongodb.uri=${SPRING_MONGODB_URI}

# Mailjet
spring.mail.host=${MAILJET_SMTP_HOST:in-v3.mailjet.com}
spring.mail.port=587
spring.mail.username=${MAILJET_API_KEY}
spring.mail.password=${MAILJET_API_SECRET}
spring.mail.properties.mail.smtp.starttls.enable=true
treasury.mail.enabled=true
treasury.mail.startup.enabled=true

# Scraper (aggressivere Pausen in Prod)
treasury.scraper.jitter.min-ms=30000
treasury.scraper.jitter.max-ms=300000
treasury.scraper.throttle.min-interval-ms=20000
```

---

## 14. Tests

**Test-Profil:** `ci` (nutzt MongoDB 6 Service-Container in CI)

| Testklasse | Typ | Testet |
|---|---|---|
| `TreasuryApplicationTests` | Integration | Spring Context lädt fehlerfrei |
| `JacksonJavaTimeConfigTest` | Unit | Jackson JSR310-Serialisierung (`Instant`, `LocalDate`) |
| `EdelmetallControllerRedirectTest` | Web-MVC | Controller-Redirects |
| `EdelmetallDashboardViewTemplateTest` | Web-MVC | Thymeleaf-Template-Rendering |
| `SettingsControllerUnitTest` | Unit | Settings-Controller-Logik |
| `DisplayPriceCollectorServiceUrlTest` | Unit | URL-Generierung für verschiedene Set-Codes + Typen |
| `DisplayServiceUpdateAngeboteTest` | Unit | Angebot-Update-Logik |
| `EdelmetallServiceDashboardDefaultPriceTest` | Unit | Dashboard mit Default-Preisen |
| `EdelmetallServiceImportDuplicatesTest` | Unit | Duplikat-Erkennung via `importKey` |
| `EdelmetallServiceImportTest` | Unit | CSV-Import-Logik |
| `EdelmetallServiceInitialSnapshotAfterImportTest` | Unit | Initialer Snapshot nach Import |
| `EdelmetallServiceManualPricesParseTest` | Unit | DE-Zahlenformat-Parsing |
| `EdelmetallServiceManualPricesPersistTest` | Unit | Manuelle Preise persistieren |
| `EdelmetallServiceUpdatePricesFallbackTest` | Unit | Fallback-Preise bei API-Fehler |
| `EdelmetallServiceUpdatePricesTest` | Unit | Regulärer Preisabruf |
| `EdelmetallServiceValuationSnapshotTest` | Unit | Valuations-Snapshot-Berechnung |
| `JobScheduleServiceTest` | Unit | Cron-NextRun-Berechnung |
| `MailServiceTest` | Unit | GreenMail SMTP-Integration |
