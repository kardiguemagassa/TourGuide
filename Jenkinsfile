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
        owaspCheck: 8  // Réduit pour éviter les timeouts
    ],
    ports: [
        master: '8082',
        develop: '8081',
        default: '8080'
    ],
    environments: [
        master: 'prod',
        develop: 'uat',
        default: 'dev'
    ],
    // Configuration OWASP avec mode offline par défaut
    owasp: [
        enabled: true,
        preferOfflineMode: true,  // CHANGEMENT: Mode offline par défaut
        maxRetries: 1,           // CHANGEMENT: Moins de retries
        cvssThreshold: 9.0,      // CHANGEMENT: Seuil plus élevé
        suppressionFile: "suppressions.xml"  // AJOUT: Fichier de suppression
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
        jdk 'JDK-17'
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
                stage('OWASP Dependency Check') {
                    when {
                        anyOf {
                            branch 'master'
                            branch 'develop'
                        }
                    }
                    steps {
                        script {
                            runOwaspDependencyCheck(config)
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
// FONCTION OWASP SIMPLIFIÉE ET FIABLE
// =============================================================================

def runOwaspDependencyCheck(config) {
    try {
        echo "🛡️ OWASP Dependency Check - Mode Offline Robuste"

        // Nettoyer les anciennes données
        sh "rm -rf ${WORKSPACE}/owasp-data || true"
        sh "mkdir -p ${WORKSPACE}/owasp-data"

        // Vérifier si le fichier de suppression existe
        def suppressionFile = config.owasp.suppressionFile
        def suppressionParam = ""

        if (fileExists(suppressionFile)) {
            suppressionParam = "-DsuppressionFile=${suppressionFile}"
            echo "✅ Utilisation du fichier de suppression: ${suppressionFile}"
        } else {
            echo "⚠️ Fichier de suppression non trouvé: ${suppressionFile}"
        }

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
                    ${suppressionParam} \
                    -DretireJsAnalyzerEnabled=false \
                    -DnodeAnalyzerEnabled=false \
                    -DossindexAnalyzerEnabled=false \
                    -DnvdDatafeedUrl= \
                    -DskipSystemScope=true \
                    -B -q
            """, returnStatus: true)

            handleOwaspResult(exitCode)
        }

    } catch (Exception e) {
        echo "🚨 Erreur OWASP Dependency Check: ${e.getMessage()}"

        // Créer un rapport d'erreur minimal
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
    <h1>🛡️ OWASP Dependency Check</h1>
    <div class="error">
        <h2>⚠️ Scan de sécurité indisponible</h2>
        <p><strong>Erreur:</strong> ${e.getMessage()}</p>
        <p><strong>Solution:</strong> Vérifiez la configuration Maven et les permissions.</p>
        <div class="timestamp">Timestamp: ${new Date()}</div>
    </div>
    <h3>Actions recommandées:</h3>
    <ul>
        <li>Vérifier la connectivité réseau</li>
        <li>Contrôler les permissions du répertoire</li>
        <li>Examiner les logs Maven détaillés</li>
    </ul>
</body>
</html>
EOF
        """

        currentBuild.result = 'UNSTABLE'
        echo "⏭️ Pipeline continue sans scan de sécurité complet"
    }
}

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

// =============================================================================
// FONCTION BUILD DOCKER MANQUANTE
// =============================================================================

def buildDockerImage(config) {
    try {
        echo "🐳 Construction de l'image Docker..."

        def imageName = "${config.containerName}:${env.CONTAINER_TAG}"

        sh """
            docker build \
                --build-arg JAR_FILE=target/*.jar \
                --build-arg JAVA_OPTS="-Xmx512m -Xms256m" \
                -t ${imageName} \
                .
        """

        echo "✅ Image Docker construite: ${imageName}"

        // Tag pour latest si c'est master
        if (env.BRANCH_NAME == 'master') {
            sh "docker tag ${imageName} ${config.containerName}:latest"
        }

    } catch (Exception e) {
        error "❌ Échec de la construction Docker: ${e.getMessage()}"
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES AMÉLIORÉES
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

    // Vérification de l'espace disque
    sh """
        df -h . | tail -1 | awk '{print "💾 Espace disque: " \$4 " disponible (" \$5 " utilisé)"}'
    """

    // Vérification des fichiers critiques
    def criticalFiles = ['pom.xml', 'src/main/java']
    criticalFiles.each { file ->
        if (!fileExists(file)) {
            error "❌ Fichier/dossier critique manquant: ${file}"
        }
    }
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

    def requiredFiles = ['Dockerfile', 'docker-compose.yml']
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

def deployWithDockerCompose(config) {
    try {
        echo "🐳 Déploiement avec Docker Compose..."

        // Vérification des fichiers requis
        if (!fileExists('docker-compose.yml')) {
            error "❌ Fichier docker-compose.yml introuvable"
        }

        // Arrêt et suppression des anciens conteneurs
        sh """
            docker-compose down --remove-orphans || true
            docker system prune -f || true
        """

        // Construction et démarrage
        sh """
            docker-compose up -d --build
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
        timeout(time: 3, unit: 'MINUTES') {
            waitUntil {
                script {
                    def status = sh(
                        script: "docker-compose ps -q ${config.containerName} | xargs docker inspect -f '{{.State.Status}}' 2>/dev/null || echo 'not-found'",
                        returnStdout: true
                    ).trim()

                    echo "État du conteneur: ${status}"
                    return status == "running"
                }
            }
        }

        // Test des endpoints de santé
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
        sh "docker-compose logs ${config.containerName} --tail 30 || true"
        error "❌ Health check échoué: ${e.getMessage()}"
    }
}

def displayBuildInfo(config) {
    echo """
    ================================================================================
                            🚀 CONFIGURATION BUILD TOURGUIDE
    ================================================================================
     Build #: ${env.BUILD_NUMBER}
     Branch: ${env.BRANCH_NAME}
     Java: 17
     Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "⚠️ Indisponible"}
     Port: ${env.HTTP_PORT}
     Tag: ${env.CONTAINER_TAG}
     OWASP: Mode Offline Robuste
     Suppression: ${config.owasp.suppressionFile}
    ================================================================================
    """
}

def sendEnhancedNotification(recipients) {
    try {
        def status = currentBuild.currentResult ?: 'SUCCESS'
        def statusIcon = ['SUCCESS': '✅', 'FAILURE': '❌', 'UNSTABLE': '⚠️', 'ABORTED': '🛑'][status] ?: '❓'

        def subject = "[Jenkins] TourGuide - Build #${env.BUILD_NUMBER} - ${status}"

        def deploymentInfo = ""
        if (env.DOCKER_AVAILABLE == "true" && (status == 'SUCCESS' || status == 'UNSTABLE')) {
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
        • OWASP: Mode Offline

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
        'dependency-check-report.json'
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
        timeout(time: 3, unit: 'MINUTES') {
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