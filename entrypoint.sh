
#!/bin/bash
# entrypoint.sh

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

# Vérification que le JAR existe
JAR_FILE="tourguide-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: JAR file $JAR_FILE not found!"
    ls -la /opt/app/
    exit 1
fi

echo "📦 JAR file found: $JAR_FILE"

# Délai optionnel
if [ -n "$STARTUP_DELAY" ]; then
    echo "⏳ Waiting ${STARTUP_DELAY}s before starting..."
    sleep "$STARTUP_DELAY"
fi

# Configuration JVM optimisée
JVM_OPTS="${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom"

# Debug mode si demandé
if [ "$DEBUG_MODE" = "true" ]; then
    echo "🐛 Debug mode enabled"
    JVM_OPTS="$JVM_OPTS -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

echo "⚙️ Final JVM Options: $JVM_OPTS"
echo "🌟 Application starting on port ${SERVER_PORT:-8080}..."
echo "=================================="

# CORRECTION: Forcer le port selon la variable d'environnement
exec java $JVM_OPTS \
    -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
    -Dserver.port="${SERVER_PORT:-8080}" \
    -Dmanagement.server.port="${SERVER_PORT:-8080}" \
    -jar "$JAR_FILE" \
    "$@"