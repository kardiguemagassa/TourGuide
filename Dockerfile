FROM eclipse-temurin:17-jre-alpine

# Metadata pour traçabilité
LABEL maintainer="magassakara@gmail.com"
LABEL version="1.0"
LABEL description="TourGuide Application"

# Arguments de build
ARG JAR_FILE=target/tourguide-0.0.1-SNAPSHOT.jar
ARG BUILD_DATE
ARG VCS_REF
ARG BUILD_NUMBER

# Labels OpenContainers
LABEL org.opencontainers.image.created=${BUILD_DATE}
LABEL org.opencontainers.image.revision=${VCS_REF}
LABEL org.opencontainers.image.version=${BUILD_NUMBER}

# Installation des outils nécessaires
RUN apk --no-cache add curl bash

# Création d'un utilisateur non-root pour la sécurité
RUN addgroup -g 1000 -S spring && \
    adduser -u 1000 -S spring -G spring

# Variables d'environnement par défaut
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

# Répertoire de travail et logs
WORKDIR /opt/app
RUN mkdir -p /opt/app/logs && chown -R spring:spring /opt/app

# Copie des fichiers avec les bonnes permissions
COPY --chown=spring:spring ${JAR_FILE} tourguide-0.0.1-SNAPSHOT.jar
COPY --chown=spring:spring entrypoint.sh entrypoint.sh

# Permissions d'exécution
RUN chmod +x entrypoint.sh

# Basculer vers l'utilisateur non-root
USER spring:spring

# Health check intégré - CORRECTION: Port dynamique
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# CORRECTION: Exposer plusieurs ports possibles
EXPOSE 8080 8091 8092

# Point d'entrée
ENTRYPOINT ["./entrypoint.sh"]
