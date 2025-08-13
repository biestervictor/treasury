# Build-Container
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /app
# Projektdateien kopieren
COPY pom.xml .
COPY src ./src

# Playwright-Browser-Dependencies installieren
RUN mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps"

# Anwendung bauen
RUN mvn clean package -DskipTests

# Runtime-Container
FROM eclipse-temurin:21-jre
WORKDIR /app

# Playwright-Dependencies auch im Runtime-Container installieren
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    libglib2.0-0 \
    libnss3 \
    libnspr4 \
    libdbus-1-3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxcb1 \
    libxkbcommon0 \
    libatspi2.0-0 \
    libx11-6 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libpango-1.0-0 \
    libcairo2 \
    libasound2t64 \
    libxshmfence1 && \
    rm -rf /var/lib/apt/lists/*

# Kopiere das erstellte JAR-File aus dem Build-Container
COPY --from=build /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 30800

ENTRYPOINT ["java", "-jar", "app.jar"]