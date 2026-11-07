# Build stage: compile and package the app with the Gradle wrapper (JDK needed here only)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
# cache mount keeps Gradle and downloaded dependencies between builds, so rebuilds are fast
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

# Runtime stage: only a JRE and the jar, no build tools or sources
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system app
USER app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
