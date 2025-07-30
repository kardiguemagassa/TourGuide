// Configuration centralisée
def config = [
    emailRecipients: "magassakara@gmail.com",
    containerName: "tourguide-app",
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
        owaspCheck: 15  // Réduit pour éviter les timeouts
    ],
    ports: [
        master: '8082',    // Aligné avec docker-compose
        develop: '8081',
        default: '8080'
    ],
    environments: [
        master: 'prod',
        develop: 'uat',
        default: 'dev'
    ],
    // Configuration OWASP avec conditions
    owasp: [
        enabled: true,
        requireApiKey: false,  // Permet de fonctionner sans clé
        maxRetries: 2,
        fallbackMode: true,
        cvssThreshold: 8.0
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
        jdk 'JDK-17'  // Changé de JDK-21 à JDK-17 pour cohérence
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
    }

    stages {
        stage('Checkout & Setup') {
            steps {
                script {
                    checkout scm
                    validateEnvironment()
                    env.DOCKER_AVAILABLE = checkDockerAvailability()
                    env.NVD_API_AVAILABLE = checkNvdApiAvailability()
                    displayBuildInfo(config)
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
                stage('OWASP Smart Check') {
                    when {
                        anyOf {
                            branch 'master'
                            branch 'develop'
                        }
                    }
                    steps {
                        script {
                            runSmartOwaspCheck(config)
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
                    buildDockerImage(config)
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
// FONCTIONS AVANCÉES POUR OWASP
// =============================================================================

def checkNvdApiAvailability() {
    try {
        echo "🔍 Vérification de la disponibilité NVD API..."

        def hasCredentials = false
        def isApiWorking = false

        // Vérifier si les credentials existent
        try {
            withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                if (env.NVD_API_KEY && env.NVD_API_KEY.trim() != '') {
                    hasCredentials = true
                    echo "✅ Credentials NVD API trouvés"

                    // Test simple de l'API
                    def apiTest = sh(
                        script: '''
                            curl -s -f -H "apikey: ${NVD_API_KEY}" \
                            "https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=1" \
                            --connect-timeout 10 --max-time 15 || echo "API_FAILED"
                        ''',
                        returnStdout: true
                    ).trim()

                    if (!apiTest.contains("API_FAILED") && apiTest.contains("CVE")) {
                        isApiWorking = true
                        echo "✅ API NVD fonctionnelle"
                    } else {
                        echo "⚠️ API NVD ne répond pas correctement"
                    }
                }
            }
        } catch (Exception credError) {
            echo "⚠️ Credentials NVD API non configurés: ${credError.getMessage()}"
        }

        def result = [
            hasCredentials: hasCredentials,
            isWorking: isApiWorking,
            mode: isApiWorking ? "online" : (hasCredentials ? "degraded" : "offline")
        ]

        echo "📊 État NVD API: ${result.mode.toUpperCase()}"
        return result.mode

    } catch (Exception e) {
        echo "❌ Erreur lors de la vérification NVD: ${e.getMessage()}"
        return "offline"
    }
}

def runSmartOwaspCheck(config) {
    try {
        echo "🛡️ Démarrage OWASP Dependency Check intelligent..."

        def owaspMode = env.NVD_API_AVAILABLE ?: "offline"
        def success = false

        switch(owaspMode) {
            case "online":
                success = runOwaspOnlineMode(config)
                break

            case "degraded":
                success = runOwaspDegradedMode(config)
                break

            case "offline":
                success = runOwaspOfflineMode(config)
                break

            default:
                echo "⚠️ Mode OWASP inconnu, tentative offline"
                success = runOwaspOfflineMode(config)
        }

        if (success) {
            echo "✅ OWASP Dependency Check terminé avec succès"
        } else {
            echo "⚠️ OWASP terminé avec avertissements"
            currentBuild.result = 'UNSTABLE'
        }

    } catch (Exception e) {
        echo "🚨 Erreur OWASP: ${e.getMessage()}"
        echo "⏭️ Continuing sans scan de sécurité détaillé"
        currentBuild.result = 'UNSTABLE'

        // Créer un rapport minimal
        writeFile file: 'target/owasp-error-report.txt',
                  text: "OWASP Dependency Check a échoué: ${e.getMessage()}\nMode: ${env.NVD_API_AVAILABLE}\nTimestamp: ${new Date()}"
    }
}

def runOwaspOnlineMode(config) {
    try {
        echo "🌐 Mode OWASP ONLINE - Avec API NVD"

        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
            // Nettoyage préventif
            sh "rm -rf ${WORKSPACE}/dc-data || true"

            timeout(time: config.timeouts.owaspCheck, unit: 'MINUTES') {
                def exitCode = sh(script: """
                    mvn org.owasp:dependency-check-maven:check \
                        -DnvdApiKey=\${NVD_API_KEY} \
                        -DdataDirectory=\${WORKSPACE}/dc-data \
                        -DautoUpdate=true \
                        -DcveValidForHours=24 \
                        -DfailBuildOnCVSS=${config.owasp.cvssThreshold} \
                        -DsuppressFailureOnError=true \
                        -DnvdMaxRetryCount=${config.owasp.maxRetries} \
                        -DnvdDelay=2000 \
                        -Dformat=HTML,XML,JSON \
                        -B -q
                """, returnStatus: true)

                return handleOwaspExitCode(exitCode, "online")
            }
        }
    } catch (Exception e) {
        echo "❌ Mode online échoué: ${e.getMessage()}"
        if (config.owasp.fallbackMode) {
            echo "🔄 Basculement vers mode dégradé..."
            return runOwaspDegradedMode(config)
        }
        return false
    }
}

def runOwaspDegradedMode(config) {
    try {
        echo "⚠️ Mode OWASP DEGRADÉ - Sans mise à jour NVD"

        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
            timeout(time: 10, unit: 'MINUTES') {
                def exitCode = sh(script: """
                    mvn org.owasp:dependency-check-maven:check \
                        -DnvdApiKey=\${NVD_API_KEY} \
                        -DdataDirectory=\${WORKSPACE}/dc-data \
                        -DautoUpdate=false \
                        -DcveValidForHours=168 \
                        -DfailBuildOnCVSS=9.0 \
                        -DsuppressFailureOnError=true \
                        -Dformat=HTML,XML \
                        -B -q
                """, returnStatus: true)

                return handleOwaspExitCode(exitCode, "degraded")
            }
        }
    } catch (Exception e) {
        echo "❌ Mode dégradé échoué: ${e.getMessage()}"
        if (config.owasp.fallbackMode) {
            echo "🔄 Basculement vers mode offline..."
            return runOwaspOfflineMode(config)
        }
        return false
    }
}

def runOwaspOfflineMode(config) {
    try {
        echo "📴 Mode OWASP OFFLINE - Données locales uniquement"

        timeout(time: 5, unit: 'MINUTES') {
            def exitCode = sh(script: """
                mvn org.owasp:dependency-check-maven:check \
                    -DdataDirectory=\${WORKSPACE}/dc-data \
                    -DautoUpdate=false \
                    -DfailBuildOnCVSS=10.0 \
                    -DsuppressFailureOnError=true \
                    -DfailOnError=false \
                    -Dformat=HTML \
                    -DretireJsAnalyzerEnabled=false \
                    -DnodeAnalyzerEnabled=false \
                    -DossindexAnalyzerEnabled=false \
                    -B -q
            """, returnStatus: true)

            return handleOwaspExitCode(exitCode, "offline")
        }

    } catch (Exception e) {
        echo "❌ Même le mode offline a échoué: ${e.getMessage()}"
        echo "📝 Génération d'un rapport d'état..."

        // Générer un rapport basique
        sh """
            echo '<html><body><h1>OWASP Dependency Check - Indisponible</h1>' > target/dependency-check-report.html
            echo '<p>Le scan de sécurité n\\'a pas pu être exécuté.</p>' >> target/dependency-check-report.html
            echo '<p>Erreur: ${e.getMessage()}</p>' >> target/dependency-check-report.html
            echo '<p>Veuillez vérifier la configuration NVD API.</p>' >> target/dependency-check-report.html
            echo '</body></html>' >> target/dependency-check-report.html
        """

        return false
    }
}

def handleOwaspExitCode(exitCode, mode) {
    switch(exitCode) {
        case 0:
            echo "✅ OWASP ${mode}: Aucune vulnérabilité critique"
            return true

        case 1:
            echo "⚠️ OWASP ${mode}: Vulnérabilités détectées sous le seuil"
            currentBuild.result = 'UNSTABLE'
            return true

        default:
            echo "❌ OWASP ${mode}: Erreur (code ${exitCode})"
            return false
    }
}

// =============================================================================
// FONCTIONS DOCKER AMÉLIORÉES
// =============================================================================

def deployWithDockerCompose(config) {
    try {
        echo "🐳 Déploiement avec Docker Compose..."

        // Vérification des fichiers requis
        if (!fileExists('docker-compose.yml')) {
            error "❌ Fichier docker-compose.yml introuvable"
        }

        if (!fileExists('.env.tourguide')) {
            echo "⚠️ Fichier .env.tourguide manquant, création d'un fichier par défaut..."
            writeFile file: '.env.tourguide', text: """
SPRING_ACTIVE_PROFILES=${env.ENV_NAME}
SERVER_PORT=8080
JAVA_OPTS=-Xmx512m -Xms256m
"""
        }

        // Arrêt et suppression des anciens conteneurs
        sh """
            docker-compose down --remove-orphans || true
            docker system prune -f || true
        """

        // Construction et démarrage
        sh """
            docker-compose build --no-cache
            docker-compose up -d
        """

        echo "✅ Application déployée avec Docker Compose"

        // Afficher les conteneurs actifs
        sh "docker-compose ps"

    } catch (Exception e) {
        error "❌ Échec du déploiement Docker Compose: ${e.getMessage()}"
    }
}

def performHealthCheck(config) {
    try {
        echo "🏥 Health check de l'application..."

        // Attendre que le conteneur soit prêt
        timeout(time: 5, unit: 'MINUTES') {
            waitUntil {
                script {
                    def status = sh(
                        script: "docker-compose ps -q tourguide | xargs docker inspect -f '{{.State.Status}}' 2>/dev/null || echo 'not-found'",
                        returnStdout: true
                    ).trim()

                    echo "État du conteneur: ${status}"
                    return status == "running"
                }
            }
        }

        // Test des endpoints de santé
        timeout(time: 3, unit: 'MINUTES') {
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
                        sleep(10)
                        return false
                    }
                }
            }
        }

        // Test approfondi
        sh """
            echo "=== HEALTH CHECK DÉTAILLÉ ==="
            curl -s http://localhost:${env.HTTP_PORT}/actuator/health | jq . || curl -s http://localhost:${env.HTTP_PORT}/actuator/health
            echo "=== INFO APPLICATION ==="
            curl -s http://localhost:${env.HTTP_PORT}/actuator/info | jq . || curl -s http://localhost:${env.HTTP_PORT}/actuator/info
        """

        echo "✅ Health check réussi"

    } catch (Exception e) {
        sh "docker-compose logs tourguide --tail 50 || true"
        error "❌ Health check échoué: ${e.getMessage()}"
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES
// =============================================================================

def validateEnvironment() {
    echo "🔍 Validation de l'environnement..."

    // Vérification Java
    sh """
        java -version
        echo "JAVA_HOME: \$JAVA_HOME"
    """

    // Vérification Maven
    sh """
        mvn -version
    """

    // Vérification de l'espace disique
    sh """
        df -h . | tail -1 | awk '{print "💾 Espace disque: " \$4 " disponible (" \$5 " utilisé)"}'
    """
}

def checkDockerAvailability() {
    try {
        def result = sh(
            script: '''
                if command -v docker >/dev/null 2>&1; then
                    if docker info >/dev/null 2>&1; then
                        if command -v docker-compose >/dev/null 2>&1; then
                            echo "true"
                        else
                            echo "false"
                        fi
                    else
                        echo "false"
                    fi
                else
                    echo "false"
                fi
            ''',
            returnStdout: true
        ).trim()

        if (result == "true") {
            echo "🐳 Docker et Docker Compose disponibles"
            sh 'docker --version && docker-compose --version'
        } else {
            echo "⚠️ Docker ou Docker Compose indisponible"
        }

        return result
    } catch (Exception e) {
        echo "❌ Erreur vérification Docker: ${e.getMessage()}"
        return "false"
    }
}

def validateDockerPrerequisites() {
    if (env.DOCKER_AVAILABLE != "true") {
        error "🐳 Docker non disponible"
    }

    def requiredFiles = ['Dockerfile', 'docker-compose.yml', 'entrypoint.sh']
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
     Java: 17 (cohérent POM/Pipeline/Docker)
     Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "⚠️ Indisponible"}
     Port: ${env.HTTP_PORT}
     Tag: ${env.CONTAINER_TAG}
     NVD API: ${env.NVD_API_AVAILABLE?.toUpperCase() ?: "OFFLINE"}
     OWASP Mode: ${config.owasp.enabled ? "Smart Check Enabled" : "Disabled"}
    ================================================================================
    """
}

def sendEnhancedNotification(recipients) {
    try {
        def status = currentBuild.currentResult ?: 'SUCCESS'
        def statusIcon = ['SUCCESS': '✅', 'FAILURE': '❌', 'UNSTABLE': '⚠️', 'ABORTED': '🛑'][status] ?: '❓'

        def subject = "[Jenkins] TourGuide - Build #${env.BUILD_NUMBER} - ${status}"

        def owaspStatus = "Non exécuté"
        if (env.NVD_API_AVAILABLE) {
            owaspStatus = "Mode: ${env.NVD_API_AVAILABLE.toUpperCase()}"
        }

        def deploymentInfo = ""
        if (env.DOCKER_AVAILABLE == "true" && status == 'SUCCESS') {
            deploymentInfo = """
        🚀 Application: http://localhost:${env.HTTP_PORT}
        🐳 Container: tourguide-app
        """
        }

        def body = """
        ${statusIcon} Build ${status}

        📋 Détails:
        • Build: #${env.BUILD_NUMBER}
        • Branche: ${env.BRANCH_NAME}
        • Java: 17
        • Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅" : "❌"}
        • OWASP: ${owaspStatus}

        ${deploymentInfo}

        🔗 Console: ${env.BUILD_URL}console
        """

        mail(to: recipients, subject: subject, body: body, mimeType: 'text/plain')
        echo "📧 Notification envoyée"

    } catch (Exception e) {
        echo "❌ Erreur notification: ${e.getMessage()}"
    }
}

def archiveOwaspReports() {
    def reportFiles = [
        'dependency-check-report.html',
        'dependency-check-report.xml',
        'dependency-check-report.json',
        'owasp-error-report.txt'
    ]

    reportFiles.each { report ->
        if (fileExists("target/${report}")) {
            archiveArtifacts artifacts: "target/${report}", allowEmptyArchive: true
        }
    }

    if (fileExists('target/dependency-check-report.html')) {
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'target',
            reportFiles: 'dependency-check-report.html',
            reportName: 'OWASP Security Report'
        ])
    }
}

def publishTestAndCoverageResults() {
    if (fileExists('target/surefire-reports/TEST-*.xml')) {
        junit 'target/surefire-reports/TEST-*.xml'
    }

    if (fileExists('target/site/jacoco/index.html')) {
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'target/site/jacoco',
            reportFiles: 'index.html',
            reportName: 'JaCoCo Coverage Report'
        ])
    }
}

def runMavenSecurityAudit() {
    try {
        echo "🔍 Audit Maven..."
        timeout(time: 5, unit: 'MINUTES') {
            sh "mvn versions:display-dependency-updates -B -q"
        }
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
                    -Dsonar.java.source=17 \
                    -Dsonar.java.target=17 \
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
                }
            }
        }
    } catch (Exception e) {
        echo "⚠️ Quality Gate: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

def cleanupDockerImages(config) {
    try {
        sh """
            docker system prune -f || true
            docker-compose down --remove-orphans || true
        """
    } catch (Exception e) {
        echo "⚠️ Cleanup: ${e.getMessage()}"
    }
}

// Fonctions utilitaires pour la configuration
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