# Build stage: compile the multi-project build and produce the boot jar.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :catalogue-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
# Non-root user (security baseline §14).
RUN useradd -r -u 1001 appuser
COPY --from=build /workspace/catalogue-service/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
