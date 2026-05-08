FROM mcr.microsoft.com/playwright/java:v1.58.0-noble
WORKDIR /app

# JAR is built by CI (mvn clean install) before docker build
COPY target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 30800

ENTRYPOINT ["java", "-jar", "app.jar"]
