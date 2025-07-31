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
        owaspCheck: 8
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
        preferOfflineMode: true,
        maxRetries: 1,
        cvssThreshold: 9.0
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

        // voir toutes les variables disponibles en ajoutant dans ton Jenkinsfile :
        stage('Show env') {
            steps {
                script {
                    sh 'printenv'   // Linux
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
// FONCTION DOCKER AVAILABILITY AMÉLIORÉE
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
            echo "🔍 Vérification de l'installation Docker..."

            try {
                sh '''
                    if command -v apt-get >/dev/null 2>&1; then
                        echo "📦 Installation Docker via apt..."
                        sudo apt-get update -y
                        sudo apt-get install -y docker.io docker-compose
                    elif command -v yum >/dev/null 2>&1; then
                        echo "📦 Installation Docker via yum..."
                        sudo yum install -y docker docker-compose
                    elif command -v brew >/dev/null 2>&1; then
                        echo "📦 Installation Docker via brew..."
                        brew install docker docker-compose
                    else
                        echo "⚠️ Gestionnaire de paquets non supporté"
                    fi
                '''

                def result = sh(script: "command -v docker 2>/dev/null || echo 'not-found'", returnStdout: true).trim()
                if (result != 'not-found') {
                    dockerFound = true
                    dockerPath = result
                }
            } catch (Exception e) {
                echo "❌ Impossible d'installer Docker automatiquement: ${e.getMessage()}"
            }
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
                    echo "❌ Docker daemon non actif - tentative de démarrage..."
                    try {
                        sh "sudo systemctl start docker || sudo service docker start || true"
                        sleep(5)

                        def retryCheck = sh(script: "${dockerPath} info >/dev/null 2>&1", returnStatus: true)
                        if (retryCheck == 0) {
                            echo "✅ Docker daemon démarré avec succès"
                            return "true"
                        } else {
                            echo "❌ Impossible de démarrer Docker daemon"
                            return "false"
                        }
                    } catch (Exception e) {
                        echo "❌ Erreur démarrage Docker: ${e.getMessage()}"
                        return "false"
                    }
                }
            } catch (Exception e) {
                echo "❌ Erreur vérification Docker: ${e.getMessage()}"
                return "false"
            }
        } else {
            echo "❌ Docker non disponible"
            echo """
            💡 Solutions possibles:
            1. Installer Docker: curl -fsSL https://get.docker.com -o get-docker.sh && sh get-docker.sh
            2. Ajouter l'utilisateur Jenkins au groupe docker: sudo usermod -aG docker jenkins
            3. Redémarrer le service Jenkins: sudo systemctl restart jenkins
            4. Vérifier les permissions: ls -la /var/run/docker.sock
            """
            return "false"
        }

    } catch (Exception e) {
        echo "❌ Erreur vérification Docker: ${e.getMessage()}"
        return "false"
    }
}

// =============================================================================
// FONCTION OWASP SIMPLIFIÉE SANS FICHIER DE SUPPRESSION
// =============================================================================

def runOwaspDependencyCheck(config) {
    try {
        echo "🛡️ OWASP Dependency Check - Mode Offline Sans Suppression"

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
                    -DnvdDatafeedUrl= \
                    -DskipSystemScope=true \
                    -B -q
            """, returnStatus: true)

            handleOwaspResult(exitCode)
        }

    } catch (Exception e) {
        echo "🚨 Erreur OWASP Dependency Check: ${e.getMessage()}"

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
// FONCTION BUILD DOCKER AMÉLIORÉE
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

        if (env.BRANCH_NAME == 'master') {
            sh "docker tag ${imageName} ${config.containerName}:latest"
        }

    } catch (Exception e) {
        error "❌ Échec de la construction Docker: ${e.getMessage()}"
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES CONSERVÉES
// =============================================================================

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

        if (!fileExists('docker-compose.yml')) {
            error "❌ Fichier docker-compose.yml introuvable"
        }

        createEnvFile()

        sh """
            docker-compose down --remove-orphans 2>/dev/null || true
            docker system prune -f || true
        """

        sh """
            export HTTP_PORT=${env.HTTP_PORT}
            export BUILD_NUMBER=${env.BUILD_NUMBER}
            export BRANCH_NAME=${env.BRANCH_NAME}
            export CONTAINER_TAG=${env.CONTAINER_TAG}
            docker-compose up -d --build
        """

        echo "✅ Application déployée avec Docker Compose"

        sleep(10)

        sh "docker-compose ps"

        sh "docker-compose logs --tail 20 ${config.serviceName} || true"

    } catch (Exception e) {
        sh "docker-compose logs ${config.serviceName} --tail 50 || true"
        error "❌ Échec du déploiement Docker Compose: ${e.getMessage()}"
    }
}

// ⚠️ FONCTION HEALTH CHECK CORRIGÉE
def performHealthCheck(config) {
    try {
        echo "🏥 Health check de l'application..."

        // ✅ Utilisation du bon nom de service
        timeout(time: 3, unit: 'MINUTES') {
            waitUntil {
                script {
                    def status = sh(
                        script: "docker-compose ps -q ${config.serviceName} | xargs docker inspect -f '{{.State.Status}}' 2>/dev/null || echo 'not-found'",
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
        sh "docker-compose logs ${config.serviceName} --tail 30 || true"
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
     Service: ${config.serviceName}
     OWASP: Mode Offline (Sans Suppression)
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

def createEnvFile() {
    echo "📝 Création du fichier .env..."

    sh """
        cat > .env << 'EOF'
# Configuration environnement TourGuide
BUILD_DATE=\$(date -u +'%Y-%m-%dT%H:%M:%SZ')
VCS_REF=${env.BRANCH_NAME}
BUILD_NUMBER=${env.BUILD_NUMBER}

# Configuration Application
SPRING_ACTIVE_PROFILES=prod
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport
SERVER_PORT=8080

# Port dynamique
HTTP_PORT=${env.HTTP_PORT}

# Configuration réseau
NETWORK_NAME=tourguide-network

# Configuration logging
LOG_LEVEL=INFO
LOG_PATH=/opt/app/logs
EOF
    """

    echo "✅ Fichier .env créé avec les variables d'environnement"
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