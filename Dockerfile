FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the built jar
COPY target/pdf-analyzer-*.jar app.jar

# Create non-root user
RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser
RUN chown appuser:appuser /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.config.additional-location=file:/app/config/", "-jar", "app.jar"]