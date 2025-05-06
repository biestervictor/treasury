# Verwende ein Maven-Image, um die Anwendung zu bauen
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# Kopiere die Maven-Projektdateien
COPY pom.xml .
COPY src ./src

# Baue die Anwendung
RUN mvn clean package -DskipTests

# Verwende ein leichtgewichtiges JDK-Image, um die Anwendung auszuführen
FROM eclipse-temurin:17-jre
WORKDIR /app

# Kopiere das erstellte JAR-File aus dem Build-Container
COPY --from=build /app/target/*.jar app.jar

# Exponiere den Standardport der Spring-Boot-Anwendung
EXPOSE 8080

# Starte die Anwendung
ENTRYPOINT ["java", "-jar", "app.jar"]