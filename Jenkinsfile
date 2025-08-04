// Configuration centralisée
def config = [
    emailRecipients: "magassakara@gmail.com",
    containerName: "tourguide-app",
    serviceName: "tourguide",
    dockerRegistry: "docker.io",
    sonarProjectKey: "tourguide",
    sonar: [
        communityEdition: true,
        projectKey: "tourguide",
        exclusions: [
            "**/target/**",
            "**/*.min.js",
            "**/node_modules/**",
            "**/.mvn/**"
        ]
    ],
    timeouts: [
        qualityGate: 2,
        deployment: 5,
        sonarAnalysis: 10,
        owaspCheck: 25  // Augmenté pour OWASP
    ],
    ports: [
        master: '8092',
        develop: '8091',
        default: '8090'
    ],
    environments: [
        master: 'prod',
        develop: 'uat',
        default: 'dev'
    ],
    owasp: [
        enabled: true,
        preferOfflineMode: false,  // Changé pour utiliser l'API NVD
        maxRetries: 3,
        cvssThreshold: 7.0
    ]
]

pipeline {
    agent any

    options {
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout(true)
        timestamps()
        parallelsAlwaysFailFast()
    }

    tools {
        maven 'M3'
        jdk 'JDK-21'
    }

    environment {
        DOCKER_BUILDKIT = "1"
        COMPOSE_DOCKER_CLI_BUILD = "1"
        BRANCH_NAME = "${env.BRANCH_NAME ?: 'unknown'}"
        BUILD_NUMBER = "${env.BUILD_NUMBER ?: '0'}"
        HTTP_PORT = "${getHTTPPort(env.BRANCH_NAME, config.ports)}"
        ENV_NAME = "${getEnvName(env.BRANCH_NAME, config.environments)}"
        CONTAINER_TAG = "${getTag(env.BUILD_NUMBER, env.BRANCH_NAME)}"
        SONAR_PROJECT_KEY = "${getSonarProjectKey(env.BRANCH_NAME, config.sonar)}"
        MAVEN_OPTS = "-Dmaven.repo.local=${WORKSPACE}/.m2/repository -Xmx1024m"
        PATH = "/usr/local/bin:/usr/bin:/bin:${env.PATH}"
    }

    stages {
        stage('Checkout & Setup') {
            steps {
                script {
                    checkout scm
                    validateEnvironment()
                    env.DOCKER_AVAILABLE = checkDockerAvailability()
                    displayBuildInfo(config)
                }
            }
        }

        stage('Show env') {
            steps {
                script {
                    sh 'printenv'
                }
            }
        }

        stage('Install Local Dependencies') {
            steps {
                script {
                    echo "📦 Installation des dépendances locales..."
                    sh '''
                        mvn install:install-file \
                            -Dfile=libs/gpsUtil.jar \
                            -DgroupId=gpsUtil \
                            -DartifactId=gpsUtil \
                            -Dversion=1.0.0 \
                            -Dpackaging=jar \
                            -Dmaven.repo.local=${WORKSPACE}/.m2/repository

                        mvn install:install-file \
                            -Dfile=libs/TripPricer.jar \
                            -DgroupId=tripPricer \
                            -DartifactId=tripPricer \
                            -Dversion=1.0.0 \
                            -Dpackaging=jar \
                            -Dmaven.repo.local=${WORKSPACE}/.m2/repository

                        mvn install:install-file \
                            -Dfile=libs/rewardCentral.jar \
                            -DgroupId=rewardCentral \
                            -DartifactId=rewardCentral \
                            -Dversion=1.0.0 \
                            -Dpackaging=jar \
                            -Dmaven.repo.local=${WORKSPACE}/.m2/repository
                    '''
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    echo "🏗️ Build et tests Maven..."
                    sh """
                        mvn clean verify \
                            org.jacoco:jacoco-maven-plugin:prepare-agent \
                            org.jacoco:jacoco-maven-plugin:report \
                            -DskipTests=false \
                            -Dmaven.test.failure.ignore=false \
                            -Djacoco.destFile=target/jacoco.exec \
                            -Djacoco.dataFile=target/jacoco.exec \
                            -B -U -q
                    """
                }
            }
            post {
                always {
                    script {
                        publishTestAndCoverageResults()
                    }
                }
            }
        }

        stage('Code Analysis') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                    changeRequest()
                }
            }
            steps {
                script {
                    performSonarAnalysis(config)
                }
            }
        }

        stage('Quality Gate') {
            when {
                allOf {
                    anyOf {
                        branch 'master'
                        branch 'develop'
                        changeRequest()
                    }
                    expression {
                        return fileExists('.scannerwork/report-task.txt')
                    }
                }
            }
            steps {
                script {
                    checkQualityGate(config)
                }
            }
        }

        stage('Security & Dependency Check') {
            parallel {
                stage('OWASP Dependency Check') {
                    when {
                        anyOf {
                            branch 'master'
                            branch 'develop'
                        }
                    }
                    steps {
                        script {
                            runOwaspDependencyCheckWithNVD(config)
                        }
                    }
                    post {
                        always {
                            script {
                                archiveOwaspReports()
                            }
                        }
                    }
                }

                stage('Maven Security Audit') {
                    steps {
                        script {
                            runMavenSecurityAudit()
                        }
                    }
                }
            }
        }

        stage('Docker Operations') {
            when {
                allOf {
                    anyOf {
                        branch 'master'
                        branch 'develop'
                    }
                    expression {
                        return env.DOCKER_AVAILABLE == "true"
                    }
                }
            }
            steps {
                script {
                    validateDockerPrerequisites()
                    buildDockerImageEnhanced(config)
                }
            }
        }

        stage('Deploy') {
            when {
                allOf {
                    anyOf {
                        branch 'master'
                        branch 'develop'
                    }
                    expression {
                        return env.DOCKER_AVAILABLE == "true"
                    }
                }
            }
            steps {
                script {
                    deployWithDockerCompose(config)
                }
            }
        }

        stage('Health Check') {
            when {
                allOf {
                    anyOf {
                        branch 'master'
                        branch 'develop'
                    }
                    expression {
                        return env.DOCKER_AVAILABLE == "true"
                    }
                }
            }
            steps {
                script {
                    performHealthCheck(config)
                }
            }
        }

        stage('Docker Diagnosis') {
            when {
                expression {
                    return env.DOCKER_AVAILABLE == "true" && currentBuild.result in ['FAILURE', 'UNSTABLE']
                }
            }
            steps {
                script {
                    diagnosisDockerIssues()
                }
            }
        }
    }

    post {
        always {
            script {
                try {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true
                    if (env.DOCKER_AVAILABLE == "true") {
                        cleanupDockerImages(config)
                    }
                    sendEnhancedNotification(config.emailRecipients)
                } catch (Exception e) {
                    echo "Erreur dans post always: ${e.getMessage()}"
                } finally {
                    cleanWs()
                }
            }
        }
    }
}

// =============================================================================
// FONCTION DE PUBLICATION CORRIGÉE (INSPIRÉE DE LA BRANCHE FEATURE)
// =============================================================================

def publishTestAndCoverageResults() {
    echo "📊 Publication des résultats de tests et couverture..."

    // Publication des résultats de tests JUnit
    if (fileExists('target/surefire-reports/TEST-*.xml')) {
        junit 'target/surefire-reports/TEST-*.xml'
        echo "✅ Résultats de tests JUnit publiés"
    } else {
        echo "⚠️ Aucun rapport de test trouvé"
    }

    // Publication du rapport de couverture JaCoCo HTML
    if (fileExists('target/site/jacoco/index.html')) {
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'target/site/jacoco',
            reportFiles: 'index.html',
            reportName: 'JaCoCo Coverage Report'
        ])
        echo "✅ Rapport de couverture HTML publié"
    } else {
        echo "⚠️ Rapport de couverture HTML non trouvé"
    }

    // Publication des métriques JaCoCo dans Jenkins
    if (fileExists('target/site/jacoco/jacoco.xml')) {
        try {
            step([
                $class: 'JacocoPublisher',
                execPattern: '**/target/jacoco.exec',
                classPattern: '**/target/classes',
                sourcePattern: '**/src/main/java',
                exclusionPattern: '**/test/**',
                changeBuildStatus: false,
                minimumInstructionCoverage: '0',
                minimumBranchCoverage: '0',
                minimumComplexityCoverage: '0',
                minimumLineCoverage: '0',
                minimumMethodCoverage: '0',
                minimumClassCoverage: '0'
            ])
            echo "✅ Métriques JaCoCo publiées dans Jenkins"
        } catch (Exception e) {
            echo "⚠️ Impossible de publier les métriques JaCoCo: ${e.getMessage()}"
        }
    } else {
        echo "⚠️ Fichier jacoco.xml non trouvé"
    }

    // Archivage des artefacts de couverture
    if (fileExists('target/site/jacoco/')) {
        archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
        echo "✅ Artefacts de couverture archivés"
    }
}

// =============================================================================
// FONCTION OWASP AVEC FALLBACK AUTOMATIQUE
// =============================================================================

def runOwaspDependencyCheckWithNVD(config) {
    try {
        echo "🛡️ OWASP Dependency Check avec NVD API Key..."

        // Vérifier d'abord si la clé API est configurée
        try {
            withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                if (env.NVD_API_KEY == null || env.NVD_API_KEY.trim().isEmpty() || env.NVD_API_KEY == '****') {
                    throw new Exception("Clé API NVD non configurée ou invalide")
                }
                echo "✅ Clé NVD API configurée"
            }
        } catch (Exception credException) {
            echo "⚠️ Clé API NVD non disponible ou invalide: ${credException.getMessage()}"
            echo "🔄 Basculement vers le mode OWASP sans API NVD..."
            runOwaspWithoutNVD(config)
            return
        }

        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
            sh "rm -rf ${WORKSPACE}/owasp-data || true"
            sh "mkdir -p ${WORKSPACE}/owasp-data"

            timeout(time: config.timeouts.owaspCheck, unit: 'MINUTES') {
                def exitCode = sh(script: """
                    mvn org.owasp:dependency-check-maven:check \
                        -DnvdApiKey=\${NVD_API_KEY} \
                        -DdataDirectory=${WORKSPACE}/owasp-data \
                        -DautoUpdate=true \
                        -DcveValidForHours=24 \
                        -DfailBuildOnCVSS=${config.owasp.cvssThreshold} \
                        -DsuppressFailureOnError=true \
                        -DfailOnError=false \
                        -Dformat=ALL \
                        -DprettyPrint=true \
                        -DretireJsAnalyzerEnabled=false \
                        -DnodeAnalyzerEnabled=false \
                        -DossindexAnalyzerEnabled=false \
                        -DnvdDatafeedEnabled=true \
                        -DnvdMaxRetryCount=${config.owasp.maxRetries} \
                        -DnvdDelay=4000 \
                        -DskipSystemScope=true \
                        -B -q
                """, returnStatus: true)

                handleOwaspResult(exitCode)
            }
        }

    } catch (Exception e) {
        echo "🚨 Erreur OWASP Dependency Check: ${e.getMessage()}"

        if (e.getMessage().contains("403") || e.getMessage().contains("404") || e.getMessage().contains("API") || e.getMessage().contains("Error updating the NVD Data")) {
            echo "⚠️ Problème avec l'API NVD - Basculement vers mode sans API"
            runOwaspWithoutNVD(config)
        } else {
            createOwaspErrorReport(e)
            currentBuild.result = 'UNSTABLE'
            echo "⏭️ Pipeline continue malgré l'erreur OWASP"
        }
    }
}

// FONCTION OWASP SANS API NVD (FALLBACK)
def runOwaspWithoutNVD(config) {
    try {
        echo "🛡️ OWASP Dependency Check en mode local (sans API NVD)..."

        sh "rm -rf ${WORKSPACE}/owasp-data || true"
        sh "mkdir -p ${WORKSPACE}/owasp-data"

        timeout(time: config.timeouts.owaspCheck, unit: 'MINUTES') {
            def exitCode = sh(script: """
                mvn org.owasp:dependency-check-maven:check \
                    -DdataDirectory=${WORKSPACE}/owasp-data \
                    -DautoUpdate=false \
                    -DfailBuildOnCVSS=${config.owasp.cvssThreshold} \
                    -DsuppressFailureOnError=true \
                    -DfailOnError=false \
                    -Dformat=HTML,XML \
                    -DprettyPrint=true \
                    -DretireJsAnalyzerEnabled=false \
                    -DnodeAnalyzerEnabled=false \
                    -DossindexAnalyzerEnabled=false \
                    -DnvdDatafeedEnabled=false \
                    -DskipSystemScope=true \
                    -B -q
            """, returnStatus: true)

            if (exitCode == 0) {
                echo "✅ OWASP: Analyse locale terminée avec succès"
            } else {
                echo "⚠️ OWASP: Analyse locale avec avertissements (code: ${exitCode})"
                currentBuild.result = 'UNSTABLE'
            }
        }

    } catch (Exception e) {
        echo "🚨 Erreur OWASP mode local: ${e.getMessage()}"
        createOwaspErrorReport(e)
        currentBuild.result = 'UNSTABLE'
    }
}

def createOwaspErrorReport(Exception e) {
    sh """
        mkdir -p target
        cat > target/dependency-check-report.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>OWASP Dependency Check - Erreur</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .error { color: #d32f2f; background: #ffebee; padding: 20px; border-radius: 4px; }
        .timestamp { color: #666; font-size: 0.9em; }
    </style>
</head>
<body>
    <h1>🛡️ OWASP Dependency Check - TourGuide</h1>
    <div class="error">
        <h2>⚠️ Scan de sécurité indisponible</h2>
        <p><strong>Erreur:</strong> ${e.getMessage()}</p>
        <p><strong>Build:</strong> #${env.BUILD_NUMBER}</p>
        <p><strong>Branche:</strong> ${env.BRANCH_NAME}</p>
        <div class="timestamp">Timestamp: ${new Date()}</div>
    </div>
    <h3>Actions recommandées:</h3>
    <ul>
        <li>Vérifier la clé API NVD dans Jenkins Credentials</li>
        <li>Vérifier la connectivité réseau vers api.nvd.nist.gov</li>
        <li>Obtenir une clé API gratuite sur: https://nvd.nist.gov/developers/request-an-api-key</li>
        <li>Le scan a basculé en mode local sans API NVD</li>
    </ul>
</body>
</html>
EOF
    """
}

def archiveOwaspReports() {
    echo "📋 Archivage des rapports OWASP..."

    def reportFiles = [
        'dependency-check-report.html',
        'dependency-check-report.xml',
        'dependency-check-report.json',
        'dependency-check-report.csv'
    ]

    def reportsFound = false
    reportFiles.each { report ->
        if (fileExists("target/${report}")) {
            archiveArtifacts artifacts: "target/${report}", allowEmptyArchive: true
            echo "✅ Rapport ${report} archivé"
            reportsFound = true
        }
    }

    // Publication du rapport HTML principal
    if (fileExists('target/dependency-check-report.html')) {
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'target',
            reportFiles: 'dependency-check-report.html',
            reportName: 'OWASP Security Report'
        ])
        echo "✅ Rapport OWASP HTML publié"
    } else {
        echo "⚠️ Aucun rapport OWASP HTML trouvé"
    }

    if (!reportsFound) {
        echo "⚠️ Aucun rapport OWASP généré"
    }
}

// =============================================================================
// FONCTION CREATEENVFILE CORRIGÉE POUR GESTION DES PORTS
// =============================================================================

def createEnvFile() {
    echo "📝 Création du fichier .env pour Docker Compose..."

    // Déterminer le port interne selon l'environnement
    def internalPort = "8080"  // Défaut
    switch(env.ENV_NAME) {
        case 'prod':
            internalPort = "8092"
            break
        case 'uat':
            internalPort = "8091"
            break
        case 'dev':
        default:
            internalPort = "8090"
            break
    }

    echo "🔧 Configuration ports:"
    echo "  - Environnement: ${env.ENV_NAME}"
    echo "  - Port externe: ${env.HTTP_PORT}"
    echo "  - Port interne: ${internalPort}"

    sh """
        cat > .env << 'EOF'
# Configuration environnement TourGuide - Build #${env.BUILD_NUMBER}
BUILD_DATE=\$(date -u +'%Y-%m-%dT%H:%M:%SZ')
VCS_REF=${env.BRANCH_NAME}
BUILD_NUMBER=${env.BUILD_NUMBER}
CONTAINER_TAG=${env.CONTAINER_TAG}

# Configuration Application Spring Boot
SPRING_PROFILES_ACTIVE=${env.ENV_NAME}
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0

# Configuration des ports
HTTP_PORT=${env.HTTP_PORT}
INTERNAL_PORT=${internalPort}
SERVER_PORT=${internalPort}

# Configuration Docker
CONTAINER_NAME=tourguide-app
SERVICE_NAME=tourguide
IMAGE_NAME=tourguide-app:${env.CONTAINER_TAG}

# Configuration réseau
NETWORK_NAME=tourguide-network

# Configuration logging
LOG_LEVEL=INFO
LOG_PATH=/opt/app/logs

# Configuration Actuator
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
MANAGEMENT_SERVER_PORT=${internalPort}

# Informations de l'application
APP_NAME=TourGuide
APP_VERSION=0.0.1-SNAPSHOT
APP_ENVIRONMENT=${env.ENV_NAME}

# Variables spécifiques à l'environnement
BRANCH_NAME=${env.BRANCH_NAME}
ENV_NAME=${env.ENV_NAME}
EOF
    """

    echo "✅ Fichier .env créé avec la configuration pour l'environnement ${env.ENV_NAME}"

    // Affichage pour debug
    sh """
        echo "📋 Contenu du fichier .env créé:"
        echo "================================"
        cat .env
        echo "================================"
    """
}

// =============================================================================
// FONCTION DEPLOYWITHDOCKERCOMPOSE CORRIGÉE POUR macOS
// =============================================================================

def deployWithDockerCompose(config) {
    try {
        echo "🐳 Déploiement avec Docker Compose..."
        echo "🔧 Configuration déploiement:"
        echo "  - Branche: ${env.BRANCH_NAME}"
        echo "  - Environnement: ${env.ENV_NAME}"
        echo "  - Port externe: ${env.HTTP_PORT}"
        echo "  - Container tag: ${env.CONTAINER_TAG}"

        // Vérification des prérequis
        if (!fileExists('docker-compose.yml')) {
            error "❌ Fichier docker-compose.yml introuvable"
        }

        // Créer le fichier .env avec la configuration corrigée
        createEnvFile()

        // Vérification des ports (compatible macOS)
        echo "🔍 Vérification des ports..."
        sh """
            echo "Vérification du port ${env.HTTP_PORT}:"
            # macOS compatible port check
            if lsof -i :${env.HTTP_PORT} >/dev/null 2>&1; then
                echo "⚠️ Port ${env.HTTP_PORT} déjà utilisé:"
                lsof -i :${env.HTTP_PORT} || true
                echo "🛑 Tentative de libération du port..."
                lsof -ti:${env.HTTP_PORT} | xargs kill -9 2>/dev/null || true
                sleep 2
            else
                echo "✅ Port ${env.HTTP_PORT} disponible"
            fi
        """

        // Arrêt propre des conteneurs existants
        echo "🛑 Arrêt des conteneurs existants..."
        sh """
            # Arrêter tous les conteneurs TourGuide existants
            docker ps -a --filter "name=tourguide" --format "{{.Names}}" | xargs docker rm -f 2>/dev/null || true

            # Arrêt via docker-compose
            docker-compose down --remove-orphans 2>/dev/null || true

            # Nettoyage des conteneurs orphelins
            docker container prune -f || true

            # Attendre que les ports se libèrent
            sleep 5
        """

        // Vérification de l'image
        def imageName = "${config.containerName}:${env.CONTAINER_TAG}"
        echo "🔍 Vérification de l'image Docker: ${imageName}"
        sh """
            if ! docker images ${imageName} --format "table {{.Repository}}:{{.Tag}}" | grep -q "${imageName}"; then
                echo "❌ Image ${imageName} non trouvée"
                echo "📋 Images disponibles:"
                docker images | grep ${config.containerName} || echo "Aucune image ${config.containerName} trouvée"
                exit 1
            else
                echo "✅ Image ${imageName} trouvée"
            fi
        """

        // CORRECTION: Créer un docker-compose temporaire qui utilise l'image construite
        echo "🚀 Création d'un docker-compose temporaire qui utilise l'image construite..."
        sh """
            # Backup du docker-compose original
            cp docker-compose.yml docker-compose.yml.backup

            # Créer un docker-compose temporaire qui utilise l'image construite
            cat > docker-compose-temp.yml << 'EOFCOMPOSE'
version: '3.8'

services:
  tourguide:
    image: ${imageName}
    container_name: tourguide-app-\${BRANCH_NAME:-local}-\${BUILD_NUMBER:-dev}
    ports:
      - "\${HTTP_PORT:-8091}:\${SERVER_PORT:-8091}"
    environment:
      - JAVA_OPTS=\${JAVA_OPTS:-"-Xmx512m -Xms256m -XX:+UseContainerSupport"}
      - SERVER_PORT=\${SERVER_PORT:-8091}
      - SPRING_PROFILES_ACTIVE=\${SPRING_PROFILES_ACTIVE:-uat}
      - LOG_LEVEL=\${LOG_LEVEL:-INFO}
      - MANAGEMENT_SERVER_PORT=\${SERVER_PORT:-8091}
    networks:
      - tourguide-network
    restart: unless-stopped
    volumes:
      - app-logs:/opt/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:\${SERVER_PORT:-8091}/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

networks:
  tourguide-network:
    driver: bridge
    name: tourguide-network

volumes:
  app-logs:
    driver: local
EOFCOMPOSE
        """

        // Démarrage avec le compose temporaire
        echo "🚀 Démarrage des conteneurs..."
        sh """
            export HTTP_PORT=${env.HTTP_PORT}
            export BUILD_NUMBER=${env.BUILD_NUMBER}
            export BRANCH_NAME=${env.BRANCH_NAME}
            export CONTAINER_TAG=${env.CONTAINER_TAG}
            export VCS_REF=${env.BRANCH_NAME}
            export BUILD_DATE=\$(date -u +'%Y-%m-%dT%H:%M:%SZ')
            export SPRING_PROFILES_ACTIVE=${env.ENV_NAME}

            # Affichage pour debug
            echo "📄 Variables d'environnement:"
            echo "HTTP_PORT=\${HTTP_PORT}"
            echo "SERVER_PORT=\${SERVER_PORT:-8091}"
            echo "SPRING_PROFILES_ACTIVE=\${SPRING_PROFILES_ACTIVE}"

            # Démarrage avec le compose temporaire
            docker-compose -f docker-compose-temp.yml up -d --force-recreate --remove-orphans
        """

        echo "✅ Conteneurs démarrés"

        // Attente pour laisser le temps aux conteneurs de démarrer
        echo "⏳ Attente du démarrage des conteneurs (20 secondes)..."
        sleep(20)

        // Vérification détaillée de l'état (compatible macOS)
        echo "🔍 Vérification détaillée de l'état:"
        sh """
            echo "=== DOCKER COMPOSE PS ==="
            docker-compose -f docker-compose-temp.yml ps

            echo "=== DOCKER PS (conteneurs TourGuide) ==="
            docker ps -a --filter "name=tourguide" || echo "Aucun conteneur tourguide trouvé"

            echo "=== PORTS EN ÉCOUTE (macOS compatible) ==="
            lsof -i :${env.HTTP_PORT} || echo "Port ${env.HTTP_PORT} non en écoute"

            echo "=== LOGS DU SERVICE ${config.serviceName} ==="
            docker-compose -f docker-compose-temp.yml logs --tail 50 ${config.serviceName} || true
        """

        // Vérification finale
        def maxRetries = 3
        def containerRunning = false

        for (int i = 1; i <= maxRetries; i++) {
            echo "🔍 Tentative ${i}/${maxRetries} de vérification du conteneur..."

            def containerStatus = sh(
                script: "docker-compose -f docker-compose-temp.yml ps -q ${config.serviceName} | xargs docker inspect -f '{{.State.Status}}' 2>/dev/null || echo 'not-found'",
                returnStdout: true
            ).trim()

            echo "📊 État du conteneur (tentative ${i}): ${containerStatus}"

            if (containerStatus == "running") {
                containerRunning = true
                break
            } else {
                echo "⏳ Conteneur pas encore prêt, attente de 10 secondes..."
                sleep(10)
            }
        }

        if (containerRunning) {
            echo "✅ Application déployée avec succès !"
            echo "🌐 Application accessible sur: http://localhost:${env.HTTP_PORT}"
            echo "🏥 Health check: http://localhost:${env.HTTP_PORT}/actuator/health"
        } else {
            echo "❌ Le conteneur n'est pas en cours d'exécution"
            sh """
                echo "=== LOGS D'ERREUR DÉTAILLÉS ==="
                docker-compose -f docker-compose-temp.yml logs ${config.serviceName} || true
            """
            error "❌ Échec du démarrage du conteneur"
        }

        // Restaurer le docker-compose original
        sh "mv docker-compose.yml.backup docker-compose.yml || true"

    } catch (Exception e) {
        echo "❌ Erreur lors du déploiement:"
        sh """
            echo "=== DIAGNOSTIC D'ERREUR COMPLET (macOS) ==="

            echo "1. Logs du service:"
            docker-compose -f docker-compose-temp.yml logs ${config.serviceName} --tail 100 || true

            echo "2. État des conteneurs:"
            docker-compose -f docker-compose-temp.yml ps || true

            echo "3. Tous les conteneurs Docker:"
            docker ps -a || true

            echo "4. Ports en écoute (macOS):"
            lsof -i | grep -E ":(809|8080)" || echo "Aucun port 809x en écoute"

            echo "5. Espace disque:"
            df -h

            echo "6. Mémoire disponible (macOS):"
            vm_stat || echo "Commande vm_stat non disponible"

            echo "7. Processus Java:"
            ps aux | grep java || echo "Aucun processus Java"

            # Restaurer le docker-compose original
            mv docker-compose.yml.backup docker-compose.yml || true
        """
        error "❌ Échec du déploiement Docker Compose: ${e.getMessage()}"
    }
}

def performHealthCheck(config) {
    try {
        echo "🏥 Health check de l'application..."

        timeout(time: 3, unit: 'MINUTES') {
            waitUntil {
                script {
                    def status = sh(
                        script: "docker-compose -f docker-compose-temp.yml ps -q ${config.serviceName} | xargs docker inspect -f '{{.State.Status}}' 2>/dev/null || echo 'not-found'",
                        returnStdout: true
                    ).trim()

                    echo "État du conteneur: ${status}"
                    return status == "running"
                }
            }
        }

        timeout(time: 2, unit: 'MINUTES') {
            waitUntil {
                script {
                    def healthCheck = sh(
                        script: "curl -f -s http://localhost:${env.HTTP_PORT}/actuator/health > /dev/null",
                        returnStatus: true
                    )

                    if (healthCheck == 0) {
                        echo "✅ Application répond correctement"
                        return true
                    } else {
                        echo "⏳ Application pas encore prête..."
                        sleep(5)
                        return false
                    }
                }
            }
        }

        echo "✅ Health check réussi"

    } catch (Exception e) {
        sh "docker-compose -f docker-compose-temp.yml logs ${config.serviceName} --tail 30 || true"
        error "❌ Health check échoué: ${e.getMessage()}"
    }
}

// =============================================================================
// FONCTIONS DOCKER AMÉLIORÉES
// =============================================================================

def checkDockerAvailability() {
    try {
        echo "🐳 Vérification de Docker..."

        def dockerPaths = [
            '/usr/bin/docker',
            '/usr/local/bin/docker',
            '/opt/homebrew/bin/docker',
            'docker'
        ]

        def dockerFound = false
        def dockerPath = ""

        for (path in dockerPaths) {
            try {
                def result = sh(script: "command -v ${path} 2>/dev/null || echo 'not-found'", returnStdout: true).trim()
                if (result != 'not-found' && result != '') {
                    dockerFound = true
                    dockerPath = result
                    echo "✅ Docker trouvé à: ${dockerPath}"
                    break
                }
            } catch (Exception e) {
                // Continuer la recherche
            }
        }

        if (!dockerFound) {
            echo "❌ Docker non trouvé dans les emplacements standards"
            return "false"
        }

        if (dockerFound) {
            try {
                sh "${dockerPath} --version"
                def daemonCheck = sh(script: "${dockerPath} info >/dev/null 2>&1", returnStatus: true)

                if (daemonCheck == 0) {
                    echo "✅ Docker daemon actif"

                    try {
                        def composeCheck = sh(script: "docker-compose --version || docker compose --version", returnStatus: true)
                        if (composeCheck == 0) {
                            echo "✅ Docker Compose disponible"
                            return "true"
                        } else {
                            echo "⚠️ Docker Compose non disponible"
                            return "false"
                        }
                    } catch (Exception e) {
                        echo "⚠️ Erreur vérification Docker Compose: ${e.getMessage()}"
                        return "false"
                    }
                } else {
                    echo "❌ Docker daemon non actif"
                    return "false"
                }
            } catch (Exception e) {
                echo "❌ Erreur vérification Docker: ${e.getMessage()}"
                return "false"
            }
        }

    } catch (Exception e) {
        echo "❌ Erreur vérification Docker: ${e.getMessage()}"
        return "false"
    }
}

def buildDockerImageEnhanced(config) {
    try {
        echo "🐳 Construction améliorée de l'image Docker..."

        def imageName = "${config.containerName}:${env.CONTAINER_TAG}"
        def latestImageName = "${config.containerName}:latest"

        // Vérification du JAR
        def jarFiles = findFiles(glob: 'target/*.jar').findAll {
            it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
        }

        if (jarFiles.length == 0) {
            error "📦 Aucun JAR exécutable trouvé dans target/"
        }

        def jarFile = jarFiles[0].path
        echo "📦 JAR utilisé: ${jarFile}"

        // Vérification du Dockerfile
        if (!fileExists('Dockerfile')) {
            echo "📝 Création d'un Dockerfile par défaut..."
            createDefaultDockerfile()
        }

        // Construction avec logs détaillés
        sh """
            echo "🔨 Construction de l'image Docker..."
            echo "Image: ${imageName}"
            echo "JAR: ${jarFile}"

            docker build \
                --build-arg JAR_FILE=${jarFile} \
                --build-arg JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport" \
                --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \
                --build-arg VCS_REF=${env.BRANCH_NAME} \
                --label "build.number=${env.BUILD_NUMBER}" \
                --label "vcs.ref=${env.BRANCH_NAME}" \
                --label "build.date=\$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
                --progress=plain \
                -t ${imageName} \
                .
        """

        // Vérification de la construction
        sh """
            echo "✅ Vérification de l'image construite:"
            docker images ${imageName}

            echo "📊 Détails de l'image:"
            docker inspect ${imageName} --format='{{.Config.Labels}}'
        """

        // Tag latest pour master
        if (env.BRANCH_NAME == 'master') {
            sh """
                docker tag ${imageName} ${latestImageName}
                echo "✅ Tag 'latest' créé pour la branche master"
            """
        }

        echo "✅ Image Docker construite avec succès: ${imageName}"

    } catch (Exception e) {
        echo "❌ Erreur lors de la construction Docker:"
        sh """
            echo "=== LOGS D'ERREUR DOCKER BUILD ==="
            docker system df
            docker images | head -5
        """
        error "❌ Échec de la construction Docker: ${e.getMessage()}"
    }
}

def createDefaultDockerfile() {
    sh """
        cat > Dockerfile << 'EOF'
# Dockerfile par défaut pour TourGuide
FROM openjdk:21-jre-slim        // ← CORRECTION: était "17"

# Métadonnées
LABEL maintainer="TourGuide Team"
LABEL version="1.0"
LABEL description="TourGuide Application"

# Variables d'environnement
ENV JAVA_OPTS=""
ENV JAR_FILE=""

# Création d'un utilisateur non-root
RUN groupadd -r tourguide && useradd -r -g tourguide tourguide

# Répertoire de travail
WORKDIR /opt/app

# Installation des dépendances système
RUN apt-get update && \\
    apt-get install -y curl && \\
    rm -rf /var/lib/apt/lists/*

# Copie du JAR
ARG JAR_FILE=target/*.jar
COPY \${JAR_FILE} app.jar

# Création des répertoires et permissions
RUN mkdir -p /opt/app/logs && \\
    chown -R tourguide:tourguide /opt/app

# Utilisateur non-root
USER tourguide

# Port exposé
EXPOSE 8080 8091 8092

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \\
    CMD curl -f http://localhost:\${SERVER_PORT:-8080}/actuator/health || exit 1

# Point d'entrée
ENTRYPOINT ["sh", "-c", "java \$JAVA_OPTS -jar app.jar"]
EOF
    """
    echo "✅ Dockerfile par défaut créé avec Java 21"
}

// =============================================================================
// FONCTION DE DIAGNOSTIC DOCKER (COMPATIBLE macOS)
// =============================================================================

def diagnosisDockerIssues() {
    echo "🔍 Diagnostic des problèmes Docker..."

    sh """
        echo "=== DIAGNOSTIC DOCKER COMPLET (macOS) ==="

        echo "1. Version Docker:"
        docker --version || echo "Docker non disponible"

        echo "2. Version Docker Compose:"
        docker-compose --version || docker compose --version || echo "Docker Compose non disponible"

        echo "3. Espace disque:"
        df -h

        echo "4. Mémoire disponible (macOS):"
        vm_stat || echo "vm_stat non disponible"

        echo "5. Images Docker disponibles:"
        docker images | head -10

        echo "6. Conteneurs en cours:"
        docker ps

        echo "7. Tous les conteneurs:"
        docker ps -a | head -10

        echo "8. Réseaux Docker:"
        docker network ls

        echo "9. Volumes Docker:"
        docker volume ls

        echo "10. Fichiers dans le workspace:"
        ls -la

        echo "11. Contenu du dossier target:"
        ls -la target/ || echo "Dossier target non trouvé"

        echo "12. Processus Java en cours:"
        ps aux | grep java || echo "Aucun processus Java"

        echo "13. Ports en écoute (macOS):"
        lsof -i | grep -E ":(809|8080)" || echo "Aucun port 809x en écoute"
    """
}

// =============================================================================
// AUTRES FONCTIONS UTILITAIRES
// =============================================================================

def handleOwaspResult(exitCode) {
    switch(exitCode) {
        case 0:
            echo "✅ OWASP: Aucune vulnérabilité critique détectée"
            break
        case 1:
            echo "⚠️ OWASP: Vulnérabilités détectées mais sous le seuil configuré"
            currentBuild.result = 'UNSTABLE'
            break
        default:
            echo "❌ OWASP: Erreur lors de l'analyse (code: ${exitCode})"
            currentBuild.result = 'UNSTABLE'
            break
    }
}

def validateEnvironment() {
    echo "🔍 Validation de l'environnement..."

    sh """
        java -version
        echo "JAVA_HOME: \$JAVA_HOME"
    """

    sh """
        mvn -version
    """

    sh """
        df -h . | tail -1 | awk '{print "💾 Espace disque: " \$4 " disponible (" \$5 " utilisé)"}'
    """

    def criticalFiles = ['pom.xml', 'src/main/java']
    criticalFiles.each { file ->
        if (!fileExists(file)) {
            error "❌ Fichier/dossier critique manquant: ${file}"
        }
    }
}

def validateDockerPrerequisites() {
    if (env.DOCKER_AVAILABLE != "true") {
        error "🐳 Docker non disponible"
    }

    def requiredFiles = ['Dockerfile']
    requiredFiles.each { file ->
        if (!fileExists(file)) {
            error "📄 Fichier requis manquant: ${file}"
        }
    }

    def jarFiles = findFiles(glob: 'target/*.jar').findAll {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }

    if (jarFiles.length == 0) {
        error "📦 Aucun JAR exécutable trouvé"
    }

    echo "📦 JAR trouvé: ${jarFiles[0].path}"
}

def displayBuildInfo(config) {
    echo """
    ================================================================================
                            🚀 CONFIGURATION BUILD TOURGUIDE
    ================================================================================
     Build #: ${env.BUILD_NUMBER}
     Branch: ${env.BRANCH_NAME}
     Environment: ${env.ENV_NAME}
     Port externe: ${env.HTTP_PORT}
     Java: 21
     Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "⚠️ Indisponible"}
     Tag: ${env.CONTAINER_TAG}
     Service: ${config.serviceName}
     OWASP: Avec fallback automatique
     Coverage: JaCoCo Plugin Jenkins natif
     OS: macOS compatible

     🔧 Configuration des ports:
     • dev (default) : 8090
     • uat (develop) : 8091
     • prod (master) : 8092
    ================================================================================
    """
}

def sendEnhancedNotification(recipients) {
    try {
        def status = currentBuild.currentResult ?: 'SUCCESS'
        def statusIcon = ['SUCCESS': '✅', 'FAILURE': '❌', 'UNSTABLE': '⚠️', 'ABORTED': '🛑'][status] ?: '❓'

        def subject = "[Jenkins] TourGuide - Build #${env.BUILD_NUMBER} - ${status} (${env.BRANCH_NAME})"

        def deploymentInfo = ""
        if (env.DOCKER_AVAILABLE == "true" && (status == 'SUCCESS' || status == 'UNSTABLE')) {
            deploymentInfo = """
        🚀 DÉPLOIEMENT RÉUSSI:
        • Application: http://localhost:${env.HTTP_PORT}
        • Health Check: http://localhost:${env.HTTP_PORT}/actuator/health
        • Environnement: ${env.ENV_NAME}
        • Container: tourguide-app-${env.BRANCH_NAME}-${env.BUILD_NUMBER}

        📊 RAPPORTS:
        • Coverage JaCoCo: ${env.BUILD_URL}jacoco/
        • Coverage Report: ${env.BUILD_URL}JaCoCo_20Coverage_20Report/
        • Security OWASP: ${env.BUILD_URL}OWASP_20Security_20Report/
        """
        }

        def body = """
        ${statusIcon} BUILD ${status} - TourGuide

        📋 DÉTAILS:
        • Build: #${env.BUILD_NUMBER}
        • Branche: ${env.BRANCH_NAME}
        • Environnement: ${env.ENV_NAME}
        • Port: ${env.HTTP_PORT}
        • Java: 21
        • Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅" : "❌"}
        • OWASP: Avec fallback automatique
        • Durée: ${currentBuild.durationString ?: 'N/A'}

        ${deploymentInfo}

        🔗 LIENS:
        • Console Jenkins: ${env.BUILD_URL}console
        • Workspace: ${env.BUILD_URL}ws/

        📅 Build exécuté le ${new Date()}
        🏗️ Jenkins: ${env.JENKINS_URL}
        """

        mail(to: recipients, subject: subject, body: body, mimeType: 'text/plain')
        echo "📧 Notification envoyée à: ${recipients}"

    } catch (Exception e) {
        echo "❌ Erreur notification: ${e.getMessage()}"
    }
}

def runMavenSecurityAudit() {
    try {
        echo "🔍 Audit Maven des dépendances..."
        timeout(time: 3, unit: 'MINUTES') {
            sh """
                mvn versions:display-dependency-updates \
                    -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                    -B -q
            """
        }
        echo "✅ Audit Maven terminé"
    } catch (Exception e) {
        echo "⚠️ Audit Maven: ${e.getMessage()}"
    }
}

def performSonarAnalysis(config) {
    echo "📊 Analyse SonarQube..."
    withSonarQubeEnv('SonarQube') {
        withCredentials([string(credentialsId: 'sonartoken', variable: 'SONAR_TOKEN')]) {
            sh """
                mvn sonar:sonar \
                    -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} \
                    -Dsonar.host.url=\$SONAR_HOST_URL \
                    -Dsonar.token=\${SONAR_TOKEN} \
                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                    -Dsonar.java.source=21 \
                    -Dsonar.java.target=21 \
                    -B -q
            """
        }
    }
}

def checkQualityGate(config) {
    try {
        timeout(time: config.timeouts.qualityGate, unit: 'MINUTES') {
            def qg = waitForQualityGate()
            if (qg.status != 'OK') {
                if (env.BRANCH_NAME == 'master') {
                    error "Quality Gate échoué sur master"
                } else {
                    currentBuild.result = 'UNSTABLE'
                    echo "⚠️ Quality Gate échoué sur ${env.BRANCH_NAME}"
                }
            } else {
                echo "✅ Quality Gate réussi"
            }
        }
    } catch (Exception e) {
        echo "⚠️ Quality Gate: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

def cleanupDockerImages(config) {
    try {
        echo "🧹 Nettoyage Docker..."
        sh """
            # Arrêt des conteneurs avec le compose temporaire
            docker-compose -f docker-compose-temp.yml down --remove-orphans || true
            docker-compose down --remove-orphans || true

            # Nettoyage des images non utilisées (garde les récentes)
            docker image prune -f --filter "until=24h" || true

            # Nettoyage des conteneurs arrêtés
            docker container prune -f || true

            # Nettoyage des volumes non utilisés
            docker volume prune -f || true

            # Nettoyage des réseaux non utilisés
            docker network prune -f || true

            # Supprimer le fichier temporaire
            rm -f docker-compose-temp.yml || true
        """
        echo "✅ Nettoyage Docker terminé"
    } catch (Exception e) {
        echo "⚠️ Erreur nettoyage Docker: ${e.getMessage()}"
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES POUR LA CONFIGURATION
// =============================================================================

String getEnvName(String branchName, Map environments) {
    def branch = branchName?.toLowerCase()
    return environments[branch] ?: environments.default
}

String getHTTPPort(String branchName, Map ports) {
    def branch = branchName?.toLowerCase()
    return ports[branch] ?: ports.default
}

String getTag(String buildNumber, String branchName) {
    def safeBranch = (branchName ?: "unknown")
        .replaceAll('[^a-zA-Z0-9-]', '-')
        .toLowerCase()

    return (safeBranch == 'master') ?
        "${buildNumber}-stable" :
        "${buildNumber}-${safeBranch}-snapshot"
}

String getSonarProjectKey(String branchName, Map sonarConfig) {
    if (sonarConfig.communityEdition) {
        return sonarConfig.projectKey
    } else {
        def branch = branchName?.toLowerCase()
        return "${sonarConfig.projectKey}${branch == 'master' ? '' : '-' + branch}"
    }
}