// Fonction pour retourner la configuration
def getConfig() {
    return [
        emailRecipients: "magassakara@gmail.com",
        containerName: "tourguide-app",
        dockerRegistry: "docker.io",
        dockerHome: '/usr/local/bin',
        sonarProjectKey: "Tourguide",
        timeouts: [
            qualityGate: 2,
            deployment: 5
        ],
        ports: [
            master: '9003',
            develop: '9002',
            default: '9001'
        ],
        environments: [
            master: 'prod',
            develop: 'uat',
            default: 'dev'
        ]
    ]
}

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout(true)
        timestamps()
    }

    tools {
        maven 'Docker-M3'
        jdk 'Docker-JDK-17'
    }

    environment {
        // Chargement de la configuration
        CONFIG = getConfig()

        // Détection de l'environnement Docker
        IS_DOCKERIZED = sh(script: 'if grep -q docker /proc/1/cgroup 2>/dev/null; then echo "true"; else echo "false"; fi', returnStdout: true).trim()

        // Configuration des outils
        MAVEN_HOME = "${IS_DOCKERIZED == 'true' ? '/usr/share/maven' : tool('Docker-M3')}"
        JAVA_HOME = "${IS_DOCKERIZED == 'true' ? '/usr/lib/jvm/java-17-openjdk-amd64' : tool('Docker-JDK-17')}"
        PATH = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${env.PATH}"

        DOCKER_BUILDKIT = "1"
        COMPOSE_DOCKER_CLI_BUILD = "1"
        // Variables calculées dynamiquement
        BRANCH_NAME = "${env.BRANCH_NAME ?: 'unknown'}"
        BUILD_NUMBER = "${env.BUILD_NUMBER ?: '0'}"
        HTTP_PORT = "${getHTTPPort(env.BRANCH_NAME, CONFIG.ports)}"
        ENV_NAME = "${getEnvName(env.BRANCH_NAME, CONFIG.environments)}"
        CONTAINER_TAG = "${getTag(env.BUILD_NUMBER, env.BRANCH_NAME)}"
    }

    stages {
        stage('Checkout & Setup') {
            steps {
                script {
                    // Checkout du code
                    checkout scm

                    // Vérification de Docker
                    env.DOCKER_AVAILABLE = checkDockerAvailability()

                    // Affichage de la configuration
                    displayBuildInfo()

                    // Vérification de l'environnement
                    validateEnvironment()
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                script {
                    installLocalDependencies()
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    runMavenBuild()
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
                    performSonarAnalysis()
                }
            }
        }

        stage('Quality Gate') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                    changeRequest()
                }
            }
            steps {
                script {
                    checkQualityGate()
                }
            }
        }

        stage('Security Audit') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    runMavenSecurityAudit()
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
                    expression { env.DOCKER_AVAILABLE == 'true' }
                }
            }
            parallel {
                stage('Docker Build') {
                    steps {
                        script {
                            buildDockerImage()
                        }
                    }
                }

                stage('Docker Push') {
                    when {
                        expression { env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'develop' }
                    }
                    steps {
                        script {
                            pushDockerImage()
                        }
                    }
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
                    expression { env.DOCKER_AVAILABLE == 'true' }
                }
            }
            steps {
                script {
                    deployApplication()
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
                    expression { env.DOCKER_AVAILABLE == 'true' }
                }
            }
            steps {
                script {
                    performHealthCheck()
                }
            }
        }
    }

    post {
        always {
            script {
                postBuildActions()
            }
        }
        failure {
            script {
                sendNotification("FAILURE")
            }
        }
        success {
            script {
                sendNotification("SUCCESS")
            }
        }
        unstable {
            script {
                sendNotification("UNSTABLE")
            }
        }
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES
// =============================================================================

def checkDockerAvailability() {
    try {
        def result = sh(
            script: '''
                # Vérification avec retry
                for i in 1 2 3; do
                    if command -v docker >/dev/null 2>&1; then
                        if timeout 10 docker info >/dev/null 2>&1; then
                            echo "true"
                            exit 0
                        fi
                    fi
                    echo "Tentative $i/3 échouée, retry dans 5s..."
                    sleep 5
                done
                echo "false"
            ''',
            returnStdout: true
        ).trim()

        if (result == "true") {
            echo "✅ Docker disponible et fonctionnel"
            sh 'docker --version'
            sh 'docker info'
        } else {
            echo "❌ Docker non disponible ou non fonctionnel"
            echo "💡 Vérifiez que Docker est installé et que le daemon est démarré"
            echo "💡 Vérifiez les permissions de l'utilisateur Jenkins"
        }

        return result
    } catch (Exception e) {
        echo "❌ Erreur lors de la vérification Docker: ${e.getMessage()}"
        return "false"
    }
}

def displayBuildInfo() {
    def config = getConfig()
    echo """
    ╔══════════════════════════════════════════════════════════════════════════════╗
    ║                            CONFIGURATION BUILD                               ║
    ╠══════════════════════════════════════════════════════════════════════════════╣
    ║ 🏗️  Build #: ${env.BUILD_NUMBER}
    ║ 🌿 Branch: ${env.BRANCH_NAME}
    ║ ☕ Java: ${env.JAVA_HOME}
    ║ 📦 Maven: ${env.MAVEN_HOME}
    ║ 🐳 Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "❌ Indisponible"}
    ║ 🌍 Environnement: ${env.ENV_NAME}
    ║ 🚪 Port: ${env.HTTP_PORT}
    ║ 🏷️  Tag: ${env.CONTAINER_TAG}
    ║ 📧 Email: ${config.emailRecipients}
    ╚══════════════════════════════════════════════════════════════════════════════╝
    """
}

def validateEnvironment() {
    echo "Validation de l'environnement..."

    // Vérification des outils requis
    def requiredTools = ['mvn', 'java', 'git']
    requiredTools.each { tool ->
        try {
            sh "which ${tool}"
            echo "${tool} disponible"
        } catch (Exception e) {
            error "${tool} non trouvé dans le PATH"
        }
    }

    // Vérification de l'espace disque
    sh """
        echo "=== ESPACE DISQUE ==="
        df -h .
    """
}

def installLocalDependencies() {
    echo "📦 Installation des dépendances locales (libs/*.jar)..."

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

def runMavenBuild() {
    echo "Build et tests Maven..."

    try {
        sh """
            mvn clean verify \
                org.jacoco:jacoco-maven-plugin:prepare-agent \
                -DskipTests=false \
                -Dmaven.test.failure.ignore=false \
                -Djacoco.destFile=target/jacoco.exec \
                -Djacoco.dataFile=target/jacoco.exec \
                -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                -B -U
        """
    } catch (Exception e) {
        echo "Erreur lors du build Maven: ${e.getMessage()}"
        sh "mvn --version"
        sh "java -version"
        error "Échec du build Maven"
    }
}

def publishTestAndCoverageResults() {
    // Publication des résultats de tests
    if (fileExists('target/surefire-reports/TEST-*.xml')) {
        junit 'target/surefire-reports/TEST-*.xml'
    }

    // Publication du rapport de couverture JaCoCo
    if (fileExists('target/site/jacoco/jacoco.xml')) {
        jacoco(
            execPattern: 'target/jacoco.exec',
            classPattern: 'target/classes',
            sourcePattern: 'src/main/java',
            exclusionPattern: 'src/test/*'
        )
    }

    // Archivage des rapports HTML
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

def performSonarAnalysis() {
    def config = getConfig()
    echo "Démarrage de l'analyse SonarQube..."

    withSonarQubeEnv('SonarQube') {
        withCredentials([string(credentialsId: 'sonartoken', variable: 'SONAR_TOKEN')]) {
            def sonarCmd = """
                mvn sonar:sonar \
                    -Dsonar.projectKey=${config.sonarProjectKey} \
                    -Dsonar.host.url=\$SONAR_HOST_URL \
                    -Dsonar.token=\$SONAR_TOKEN \
                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                    -Dsonar.java.binaries=target/classes \
                    -B
            """

            if (env.BRANCH_NAME && env.BRANCH_NAME != 'master') {
                sonarCmd += " -Dsonar.branch.name=${env.BRANCH_NAME}"
            }

            sh sonarCmd
        }
    }
}

def checkQualityGate() {
    def config = getConfig()
    echo "Vérification du Quality Gate..."

    timeout(time: config.timeouts.qualityGate, unit: 'MINUTES') {
        def qg = waitForQualityGate()

        if (qg.status != 'OK') {
            echo "Quality Gate: ${qg.status}"
            if (qg.conditions) {
                qg.conditions.each { condition ->
                    echo "  • ${condition.metricName}: ${condition.actualValue} (seuil: ${condition.errorThreshold})"
                }
            }

            if (env.BRANCH_NAME == 'master') {
                error "Quality Gate échoué sur master"
            } else {
                currentBuild.result = 'UNSTABLE'
            }
        } else {
            echo "Quality Gate: PASSED"
        }
    }
}

def runMavenSecurityAudit() {
    def config = getConfig()
    echo "Audit de sécurité Maven..."

    timeout(time: config.timeouts.securityAudit, unit: 'MINUTES') {
        sh """
            mvn versions:display-dependency-updates \
                -DprocessDependencyManagement=false \
                -DgenerateBackupPoms=false \
                -B

            mvn versions:display-plugin-updates \
                -DgenerateBackupPoms=false \
                -B
        """
    }
}

def buildDockerImage() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker non disponible, skip de l'étape de build"
        return
    }

    // Trouver le fichier JAR
    def jarFile = findFiles(glob: 'target/*.jar').find {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }?.path

    if (!jarFile) {
        error "Aucun fichier JAR exécutable trouvé dans target/"
    }

    echo "Construction de l'image Docker avec ${jarFile}..."

    try {
        sh """
            docker build \
                --pull \
                --no-cache \
                --build-arg JAR_FILE=${jarFile} \
                -t "${config.containerName}:${env.CONTAINER_TAG}" \
                .
        """

        echo "Image Docker construite avec succès"
    } catch (Exception e) {
        error "Échec de la construction Docker: ${e.getMessage()}"
    }
}

def pushDockerImage() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true" || !config.dockerRegistry) {
        echo "Docker non disponible ou aucun registry configuré, skip du push"
        return
    }

    withCredentials([usernamePassword(
        credentialsId: 'dockerhub-credentials',
        usernameVariable: 'DOCKER_USER',
        passwordVariable: 'DOCKER_PASSWORD'
    )]) {
        try {
            echo "Connexion au registre Docker..."
            sh """
                echo "\${DOCKER_PASSWORD}" | docker login -u "\${DOCKER_USER}" --password-stdin ${config.dockerRegistry}
            """

            echo "Tagging et push de l'image..."
            sh """
                docker tag "${config.containerName}:${env.CONTAINER_TAG}" "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
                docker push "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
            """

            // Pour master, on push aussi le tag latest
            if (env.BRANCH_NAME == 'master') {
                sh """
                    docker tag "${config.containerName}:${env.CONTAINER_TAG}" "\${DOCKER_USER}/${config.containerName}:latest"
                    docker push "\${DOCKER_USER}/${config.containerName}:latest"
                """
            }

            echo "Déconnexion du registre..."
            sh "docker logout ${config.dockerRegistry}"

            echo "Image poussée avec succès"
        } catch (Exception e) {
            error "Échec du push Docker: ${e.getMessage()}"
        }
    }
}

def deployApplication() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker non disponible, skip du déploiement"
        return
    }

    try {
        withCredentials([usernamePassword(
            credentialsId: 'dockerhub-credentials',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASSWORD'
        )]) {

            echo "Arrêt du conteneur existant..."
            sh """
                docker stop ${config.containerName} 2>/dev/null || echo "Aucun conteneur à arrêter"
                docker rm ${config.containerName} 2>/dev/null || echo "Aucun conteneur à supprimer"
            """

            // Démarrage du nouveau conteneur
            def imageName = config.dockerRegistry ?
                "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}" :
                "${config.containerName}:${env.CONTAINER_TAG}"

            sh """
                docker run -d \
                    --name ${config.containerName} \
                    -p ${env.HTTP_PORT}:8080 \
                    -e "SPRING_PROFILES_ACTIVE=${env.ENV_NAME}" \
                    --restart unless-stopped \
                    ${imageName}
            """

            echo "Application déployée avec succès sur le port ${env.HTTP_PORT}"
        }
    } catch (Exception e) {
        error "Échec du déploiement: ${e.getMessage()}"
    }
}

def performHealthCheck() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker non disponible, skip du health check"
        return
    }

    try {
        echo "Vérification de la santé de l'application..."

        timeout(time: config.timeouts.deployment, unit: 'MINUTES') {
            waitUntil {
                def health = sh(
                    script: "curl -s http://localhost:${env.HTTP_PORT}/actuator/health | grep -q '\"status\":\"UP\"'",
                    returnStatus: true
                )

                if (health == 0) {
                    echo "Application en bonne santé"
                    return true
                } else {
                    echo "En attente du démarrage de l'application..."
                    sleep(10)
                    return false
                }
            }
        }
    } catch (Exception e) {
        error "Health check échoué: ${e.getMessage()}"
    }
}

def postBuildActions() {
    def config = getConfig()
    // Archivage des artefacts
    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true

    // Nettoyage Docker
    if (env.DOCKER_AVAILABLE == "true") {
        sh """
            docker system prune -f 2>/dev/null || true
        """
    }

    // Nettoyage du workspace
    cleanWs()
}

def sendNotification(status) {
    def config = getConfig()
    def subject = "[Jenkins] ${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - ${status}"
    def body = """
        Résultat du build: ${status}

        Détails:
        - Projet: ${env.JOB_NAME}
        - Build: #${env.BUILD_NUMBER}
        - Branche: ${env.BRANCH_NAME ?: 'N/A'}
        - Environnement: ${env.ENV_NAME}
        - Port: ${env.HTTP_PORT}

        Liens:
        - Console: ${env.BUILD_URL}console
        - Artefacts: ${env.BUILD_URL}artifact/

        ${status == 'SUCCESS' ? 'Build réussi!' : 'Veuillez vérifier les logs pour plus de détails.'}
    """

    mail(
        to: config.emailRecipients,
        subject: subject,
        body: body
    )
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