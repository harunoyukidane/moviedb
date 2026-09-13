# Runtime image. The boot jar is built on the host (./gradlew bootJar) and copied
# in — this avoids building inside the container, which is both faster and works in
# environments where the container cannot reach Maven Central / services.gradle.org
# through a TLS-intercepting proxy. The setup scripts build the jars first.
#
# Build the jar first:  (from backend/) ./gradlew :people-service:bootJar
FROM eclipse-temurin:21-jre
WORKDIR /app
# Non-root user (security baseline §14).
RUN useradd -r -u 1001 appuser
# Pre-create the person-artwork mount dir owned by appuser (see catalogue Dockerfile).
RUN mkdir -p /var/lib/artwork && chown -R appuser:appuser /var/lib/artwork
COPY people-service/build/libs/*.jar app.jar
USER appuser
EXPOSE 8081 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
