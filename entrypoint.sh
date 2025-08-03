#!/bin/bash

# Script d'entrée corrigé pour TourGuide
set -e

echo "🚀 Starting TourGuide Application..."
echo "Environment: ${SPRING_PROFILES_ACTIVE:-dev}"
echo "Java Options: ${JAVA_OPTS}"
echo "Server Port: ${SERVER_PORT:-8080}"
echo "Branch: ${BRANCH_NAME:-unknown}"
echo "Build: ${BUILD_NUMBER:-unknown}"

# Affichage des informations système
echo "📊 System Information:"
echo "  - Java Version: $(java -version 2>&1 | head -n 1)"
echo "  - Available Memory: $(free -h | grep Mem | awk '{print $2}' 2>/dev/null || echo 'N/A')"
echo "  - Available CPU Cores: $(nproc 2>/dev/null || echo 'N/A')"

# CORRECTION: Chercher le JAR avec plusieurs noms possibles
JAR_FILE=""

# Liste des noms possibles pour le JAR (par ordre de priorité)
POSSIBLE_JARS=(
    "app.jar"
    "tourguide-0.0.1-SNAPSHOT.jar"
    "tourguide.jar"
    "*.jar"
)

# Recherche du JAR
for jar_name in "${POSSIBLE_JARS[@]}"; do
    if [ -f "$jar_name" ]; then
        JAR_FILE="$jar_name"
        echo "📦 JAR trouvé: $JAR_FILE"
        break
    elif ls $jar_name 1> /dev/null 2>&1; then
        JAR_FILE=$(ls $jar_name | head -1)
        echo "📦 JAR trouvé: $JAR_FILE"
        break
    fi
done

# Vérification que le JAR existe
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: Aucun fichier JAR trouvé!"
    echo "📁 Contenu du répertoire courant:"
    ls -la
    echo "🔍 Recherche de fichiers .jar:"
    find . -name "*.jar" -type f 2>/dev/null || echo "Aucun fichier .jar trouvé"
    exit 1
fi

echo "✅ Utilisation du JAR: $JAR_FILE"

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

echo "🌟 Application starting on port ${SERVER_PORT:-8080}..."
echo "=================================="

# CORRECTION: Utilisation du JAR trouvé dynamiquement
exec java $JVM_OPTS \
    -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
    -Dserver.port="${SERVER_PORT:-8080}" \
    -Dmanagement.server.port="${SERVER_PORT:-8080}" \
    -jar "$JAR_FILE" \
    "$@"