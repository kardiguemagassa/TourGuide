FROM eclipse-temurin:21-jre-alpine

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
LABEL org.opencontainers.image.source="https://github.com/kardiguemagassa/TourGuide"
LABEL org.opencontainers.image.title="TourGuide Application"

# AMÉLIORATION 1: Installation optimisée avec nettoyage
RUN apk --no-cache add curl bash \
    && rm -rf /var/cache/apk/*

# Création d'un utilisateur non-root pour la sécurité
RUN addgroup -g 1000 -S spring && \
    adduser -u 1000 -S spring -G spring

# AMÉLIORATION 2: Variables d'environnement plus flexibles
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Europe/Paris
ENV LANG=en_US.UTF-8

# Répertoire de travail et logs
WORKDIR /opt/app

# AMÉLIORATION 3: Créer structure de répertoires complète
RUN mkdir -p /opt/app/logs /opt/app/config /opt/app/data && \
    chown -R spring:spring /opt/app

# AMÉLIORATION 4: Copie du JAR avec nom générique
COPY --chown=spring:spring ${JAR_FILE} app.jar

# AMÉLIORATION 5: Vérifier si entrypoint.sh existe avant de le copier
COPY --chown=spring:spring entrypoint.sh* ./
RUN if [ -f entrypoint.sh ]; then chmod +x entrypoint.sh; fi

# Basculer vers l'utilisateur non-root
USER spring:spring

# AMÉLIORATION 6: Health check plus robuste
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || \
        curl -f http://localhost:8080/actuator/health || \
        curl -f http://localhost:8091/actuator/health || \
        curl -f http://localhost:8092/actuator/health || exit 1

# Exposer plusieurs ports possibles
EXPOSE 8080 8091 8092

# AMÉLIORATION 7: Point d'entrée avec fallback
ENTRYPOINT ["sh", "-c", "if [ -f entrypoint.sh ]; then ./entrypoint.sh; else java $JAVA_OPTS -jar app.jar; fi"]
