#!/bin/bash

# Fixed entry script for TourGuide
set -e

echo "🚀 Starting TourGuide Application..."
echo "Environment: ${SPRING_PROFILES_ACTIVE:-dev}"
echo "Java Options: ${JAVA_OPTS}"
echo "Server Port: ${SERVER_PORT:-8080}"
echo "Branch: ${BRANCH_NAME:-unknown}"
echo "Build: ${BUILD_NUMBER:-unknown}"

# Displaying system information
echo "📊 System Information:"
echo "  - Java Version: $(java -version 2>&1 | head -n 1)"
echo "  - Available Memory: $(free -h | grep Mem | awk '{print $2}' 2>/dev/null || echo 'N/A')"
echo "  - Available CPU Cores: $(nproc 2>/dev/null || echo 'N/A')"

# FIX: Search for JAR with multiple possible names
JAR_FILE=""

# List of possible names for the JAR (in order of priority)
POSSIBLE_JARS=(
    "app.jar"
    "tourguide-0.0.1-SNAPSHOT.jar"
    "tourguide.jar"
    "*.jar"
)

# JAR Search
for jar_name in "${POSSIBLE_JARS[@]}"; do
    if [ -f "$jar_name" ]; then
        JAR_FILE="$jar_name"
        echo "📦 JAR found: $JAR_FILE"
        break
    elif ls $jar_name 1> /dev/null 2>&1; then
        JAR_FILE=$(ls $jar_name | head -1)
        echo "📦 JAR found: $JAR_FILE"
        break
    fi
done

# Checking that the JAR exists
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: No JAR file found!"
    echo "📁 Contents of the current directory:"
    ls -la
    echo "🔍Searching for .jar files :"
    find . -name "*.jar" -type f 2>/dev/null || echo "No .jar files found"
    exit 1
fi

echo "✅ Using the JAR: $JAR_FILE"

# Optional delay to allow other services to start
if [ -n "$STARTUP_DELAY" ]; then
    echo "⏳ Waiting ${STARTUP_DELAY}s before starting..."
    sleep "$STARTUP_DELAY"
fi

# Container-optimized JVM configuration
JVM_OPTS="${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom"

# Added debug options if requested
if [ "$DEBUG_MODE" = "true" ]; then
    echo "🐛 Debug mode enabled"
    JVM_OPTS="$JVM_OPTS -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

# Displaying final JVM options
echo "⚙️ Final JVM Options: $JVM_OPTS"

echo "🌟 Application starting on port ${SERVER_PORT:-8080}..."
echo "=================================="

# Using dynamically found JAR
exec java $JVM_OPTS \
    -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
    -Dserver.port="${SERVER_PORT:-8080}" \
    -Dmanagement.server.port="${SERVER_PORT:-8080}" \
    -jar "$JAR_FILE" \
    "$@"