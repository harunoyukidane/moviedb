# Runtime image. The boot jar is built on the host (./gradlew bootJar) and copied
# in — this avoids building inside the container, which is both faster and works in
# environments where the container cannot reach Maven Central / services.gradle.org
# through a TLS-intercepting proxy. The setup scripts build the jars first.
#
# Build the jar first:  (from backend/) ./gradlew :catalogue-service:bootJar
FROM eclipse-temurin:21-jre
WORKDIR /app
# cwebp: best-effort WebP transcoding for artwork (WebpEncoder). Its absence
# degrades gracefully (artwork is just served in its primary format), but
# installing it is what actually turns on Accept-negotiated WebP responses.
RUN apt-get update && apt-get install -y --no-install-recommends webp && rm -rf /var/lib/apt/lists/*
# Non-root user (security baseline §14).
RUN useradd -r -u 1001 appuser
# Pre-create the artwork mount dir owned by appuser so a fresh named volume
# mounted here is writable by the non-root process (Docker seeds a new named
# volume with the image directory's ownership).
RUN mkdir -p /var/lib/artwork && chown -R appuser:appuser /var/lib/artwork
COPY catalogue-service/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
