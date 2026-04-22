FROM eclipse-temurin:21-jre
WORKDIR /app

# Playwright OS-level dependencies
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

# JAR is built by CI (mvn clean install) before docker build
COPY target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 30800

ENTRYPOINT ["java", "-jar", "app.jar"]
