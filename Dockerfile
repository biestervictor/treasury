FROM eclipse-temurin:21-jre
WORKDIR /app

# Playwright OS-level dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    unzip \
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

# JAR is built by CI (mvn clean install) before docker build
COPY target/*.jar app.jar

# Install Playwright Chromium browser binary.
# The Playwright library is bundled in the Spring Boot fat JAR under BOOT-INF/lib/.
# We extract those JARs and run the Playwright CLI installer.
RUN mkdir /tmp/pw && \
    unzip -q app.jar "BOOT-INF/lib/*" -d /tmp/pw && \
    java -cp "/tmp/pw/BOOT-INF/lib/*" com.microsoft.playwright.CLI install chromium && \
    rm -rf /tmp/pw

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 30800

ENTRYPOINT ["java", "-jar", "app.jar"]
