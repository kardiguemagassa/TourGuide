FROM eclipse-temurin:21-jre-alpine

# Metadata for traceability
LABEL maintainer="magassakara@gmail.com"
LABEL version="1.0"
LABEL description="TourGuide Application"

# Build arguments
ARG JAR_FILE=target/tourguide-0.0.1-SNAPSHOT.jar
ARG BUILD_DATE
ARG VCS_REF
ARG BUILD_NUMBER

# OpenContainers Labels
LABEL org.opencontainers.image.created=${BUILD_DATE}
LABEL org.opencontainers.image.revision=${VCS_REF}
LABEL org.opencontainers.image.version=${BUILD_NUMBER}
LABEL org.opencontainers.image.source="https://github.com/kardiguemagassa/TourGuide"
LABEL org.opencontainers.image.title="TourGuide Application"

# Installation with cleaning
RUN apk --no-cache add curl bash \
    && rm -rf /var/cache/apk/*

# Creating a non-root user for security
RUN addgroup -g 1000 -S spring && \
    adduser -u 1000 -S spring -G spring

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Europe/Paris
ENV LANG=en_US.UTF-8

# Working directory and logs
WORKDIR /opt/app

# Create complete directory structure
RUN mkdir -p /opt/app/logs /opt/app/config /opt/app/data && \
    chown -R spring:spring /opt/app

# Copy of the JAR with generic name
COPY --chown=spring:spring ${JAR_FILE} app.jar

# Check if entrypoint.sh exists before copying it
COPY --chown=spring:spring entrypoint.sh* ./
RUN if [ -f entrypoint.sh ]; then chmod +x entrypoint.sh; fi

# Switch to non-root user
USER spring:spring

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || \
        curl -f http://localhost:8080/actuator/health || \
        curl -f http://localhost:8091/actuator/health || \
        curl -f http://localhost:8092/actuator/health || exit 1

# Expose multiple possible ports
EXPOSE 8080 8091 8092

# Entry point with fallback
ENTRYPOINT ["sh", "-c", "if [ -f entrypoint.sh ]; then ./entrypoint.sh; else java $JAVA_OPTS -jar app.jar; fi"]
