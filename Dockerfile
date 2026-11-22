# ---------------------------------------------------------------------------
# Build-Stage: kompiliert die Anwendung mit Maven
# ---------------------------------------------------------------------------
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

# ---------------------------------------------------------------------------
# Runtime-Stage: das Java-Basis-Image (ABSICHTLICH VERALTET)
# Ziel des Trainings: Minor-/Patch-Updates dieses Images automatisch mergen,
# Major-Updates (z. B. 17 -> 25) aber weiterhin manuell prüfen.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25.0.5.1_2-jre-jammy
WORKDIR /app
COPY --from=build /src/target/renovate-training.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["Renovate-Training"]
