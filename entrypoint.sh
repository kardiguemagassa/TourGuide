#!/bin/bash

# Script d'entrée amélioré pour TourGuide
set -e

echo "🚀 Starting TourGuide Application..."
echo "Environment: ${SPRING_ACTIVE_PROFILES:-dev}"
echo "Java Options: ${JAVA_OPTS}"
echo "Server Port: ${SERVER_PORT:-8080}"

# Affichage des informations système
echo "📊 System Information:"
echo "  - Java Version: $(java -version 2>&1 | head -n 1)"
echo "  - Available Memory: $(free -h | grep Mem | awk '{print $2}' 2>/dev/null || echo 'N/A')"
echo "  - Available CPU Cores: $(nproc 2>/dev/null || echo 'N/A')"

# Vérification que le JAR existe
JAR_FILE="tourguide-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: JAR file $JAR_FILE not found!"
    exit 1
fi

echo "📦 JAR file found: $JAR_FILE"

# Délai optionnel pour permettre aux autres services de démarrer
if [ -n "$STARTUP_DELAY" ]; then
    echo "⏳ Waiting ${STARTUP_DELAY}s before starting..."
    sleep "$STARTUP_DELAY"
fi

# Configuration JVM optimisée pour conteneur
JVM_OPTS="${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom"

# Ajout d'options de debug si demandé
if [ "$DEBUG_MODE" = "true" ]; then
    echo "🐛 Debug mode enabled"
    JVM_OPTS="$JVM_OPTS -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

# Affichage des options JVM finales
echo "⚙️ Final JVM Options: $JVM_OPTS"

echo "🌟 Application starting..."
echo "=================================="

# Exécution de l'application avec gestion des signaux
exec java $JVM_OPTS \
    -Dspring.profiles.active="${SPRING_ACTIVE_PROFILES:-prod}" \
    -Dserver.port="${SERVER_PORT:-8080}" \
    -jar "$JAR_FILE" \
    "$@"