// Configuration centralisée avec détection d'environnement
def config = [
    emailRecipients: "magassakara@gmail.com",
    containerName: "tourguide-app",
    dockerRegistry: "docker.io",
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
    ],
    // Configuration spécifique par environnement Jenkins
    jenkins: [
        local: [
            dockerHost: "unix:///var/run/docker.sock",
            dockerNetwork: "host",
            mavenTool: "M3",
            jdkTool: "JDK-21",
            sonarUrl: "http://localhost:9000"
        ],
        docker: [
            dockerHost: "unix:///var/run/docker.sock",
            dockerNetwork: "jenkins-network",
            mavenTool: "Docker-M3",
            jdkTool: "Docker-JDK-17",
            sonarUrl: "http://sonarqube:9000"
        ]
    ]
]

pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout(true)
        timestamps()
    }

    // Les tools seront configurés dynamiquement dans le pipeline
    tools {}

    environment {
        DOCKER_BUILDKIT = "1"
        COMPOSE_DOCKER_CLI_BUILD = "1"
        // Variables calculées dynamiquement
        BRANCH_NAME = "${env.BRANCH_NAME ?: 'unknown'}"
        BUILD_NUMBER = "${env.BUILD_NUMBER ?: '0'}"
        HTTP_PORT = "${getHTTPPort(env.BRANCH_NAME, config.ports)}"
        ENV_NAME = "${getEnvName(env.BRANCH_NAME, config.environments)}"
        CONTAINER_TAG = "${getTag(env.BUILD_NUMBER, env.BRANCH_NAME)}"
    }

    stages {
        stage('Environment Detection & Setup') {
            steps {
                script {
                    // Détection de l'environnement Jenkins
                    env.JENKINS_ENV = detectJenkinsEnvironment()
                    def jenkinsConfig = config.jenkins[env.JENKINS_ENV]

                    // Configuration dynamique des outils selon l'environnement
                    configureTools(jenkinsConfig)

                    // Checkout du code
                    checkout scm

                    // Configuration de Docker selon l'environnement
                    configureDockerEnvironment(env.JENKINS_ENV, config)

                    // Vérification de Docker avec retry
                    env.DOCKER_AVAILABLE = checkDockerAvailability()

                    // Affichage de la configuration
                    displayBuildInfo(config, env.JENKINS_ENV)
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    def jenkinsConfig = config.jenkins[env.JENKINS_ENV]

                    // Configuration des outils pour ce stage
                    withTools(jenkinsConfig) {
                        sh """
                            echo "🔧 Configuration Maven et JDK:"
                            echo "JAVA_HOME: \${JAVA_HOME}"
                            echo "PATH: \${PATH}"
                            java -version
                            mvn -version

                            echo "🏗️ Compilation et tests..."
                            mvn clean verify \
                                org.jacoco:jacoco-maven-plugin:prepare-agent \
                                -DskipTests=false \
                                -Dmaven.test.failure.ignore=false \
                                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \
                                -B -U
                        """
                    }
                }
            }
            post {
                always {
                    script {
                        // Publication des résultats de tests avec junit
                        if (fileExists('target/surefire-reports/TEST-*.xml')) {
                            junit 'target/surefire-reports/TEST-*.xml'
                        }

                        // Archivage des rapports de couverture
                        if (fileExists('target/site/jacoco/index.html')) {
                            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                            echo "✅ Rapport de couverture archivé dans les artefacts"
                        }
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
                    def jenkinsConfig = config.jenkins[env.JENKINS_ENV]

                    withSonarQubeEnv('SonarQube') {
                        withCredentials([string(credentialsId: 'sonartoken', variable: 'SONAR_TOKEN')]) {
                            withTools(jenkinsConfig) {
                                sh """
                                    echo "🔍 Analyse SonarQube avec ${jenkinsConfig.sonarUrl}..."
                                    mvn sonar:sonar \
                                        -Dsonar.projectKey=${config.sonarProjectKey} \
                                        -Dsonar.host.url=${jenkinsConfig.sonarUrl} \
                                        -Dsonar.token=\${SONAR_TOKEN} \
                                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                        -Dsonar.java.binaries=target/classes \
                                        -Dsonar.branch.name=${env.BRANCH_NAME} \
                                        -B
                                """
                            }
                        }
                    }
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
                timeout(time: config.timeouts.qualityGate, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    validateDockerPrerequisites()
                    buildDockerImage(config, env.JENKINS_ENV)
                }
            }
        }

        stage('Docker Push') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    pushDockerImage(config)
                }
            }
        }

        stage('Deploy') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    deployApplication(config, env.JENKINS_ENV)
                }
            }
        }

        stage('Health Check') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    performHealthCheck(config, env.JENKINS_ENV)
                }
            }
        }
    }

    post {
        always {
            script {
                // Nettoyage des images Docker locales
                cleanupDockerImages(config)

                // Archivage des artefacts
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true

                // Nettoyage du workspace
                cleanWs()

                // Envoi de notification
                sendNotification(config.emailRecipients, env.JENKINS_ENV)
            }
        }
        failure {
            script {
                echo "❌ Pipeline échoué - Vérifiez les logs ci-dessus"
            }
        }
        success {
            script {
                echo "✅ Pipeline réussi - Application déployée avec succès"
            }
        }
        unstable {
            script {
                echo "⚠️ Pipeline instable - Vérifiez les avertissements"
            }
        }
    }
}

// =============================================================================
// FONCTIONS DE CONFIGURATION DES OUTILS
// =============================================================================

def configureTools(jenkinsConfig) {
    try {
        echo "🔧 Configuration des outils pour Jenkins ${env.JENKINS_ENV}:"
        echo "   - Maven: ${jenkinsConfig.mavenTool}"
        echo "   - JDK: ${jenkinsConfig.jdkTool}"

        // Vérifier que les outils existent
        def availableTools = tool.getDescriptor().getInstallations()
        echo "📋 Outils disponibles dans Jenkins:"
        availableTools.each { toolInstall ->
            echo "   - ${toolInstall.name} (${toolInstall.class.simpleName})"
        }

    } catch (Exception e) {
        echo "⚠️ Erreur lors de la configuration des outils: ${e.getMessage()}"
    }
}

def withTools(jenkinsConfig, Closure body) {
    // Utilisation dynamique des outils selon l'environnement
    def mavenTool = tool name: jenkinsConfig.mavenTool, type: 'maven'
    def jdkTool = tool name: jenkinsConfig.jdkTool, type: 'jdk'

    withEnv([
        "JAVA_HOME=${jdkTool}",
        "MAVEN_HOME=${mavenTool}",
        "PATH+MAVEN=${mavenTool}/bin",
        "PATH+JAVA=${jdkTool}/bin"
    ]) {
        body()
    }
}

// =============================================================================
// FONCTIONS DE DÉTECTION ET CONFIGURATION D'ENVIRONNEMENT
// =============================================================================

def detectJenkinsEnvironment() {
    try {
        // Méthode 1: Vérifier si nous sommes dans un conteneur Docker
        if (fileExists('/.dockerenv')) {
            echo "🐳 Détection: Jenkins dans Docker (/.dockerenv trouvé)"
            return 'docker'
        }

        // Méthode 2: Vérifier la présence de variables d'environnement Docker
        def hostname = sh(script: 'hostname', returnStdout: true).trim()
        if (hostname.contains('docker') || hostname.length() == 12) {
            echo "🐳 Détection: Jenkins dans Docker (hostname: ${hostname})"
            return 'docker'
        }

        // Méthode 3: Vérifier si Jenkins_HOME contient 'docker'
        if (env.JENKINS_HOME?.contains('docker')) {
            echo "🐳 Détection: Jenkins dans Docker (JENKINS_HOME)"
            return 'docker'
        }

        // Méthode 4: Vérifier les processus Docker
        def dockerProcesses = sh(
            script: 'ps aux | grep -c "[d]ocker" || true',
            returnStdout: true
        ).trim().toInteger()

        if (dockerProcesses > 0) {
            echo "🐳 Détection: Jenkins dans Docker (processus Docker détectés: ${dockerProcesses})"
            return 'docker'
        }

        // Méthode 5: Vérifier l'existence d'outils spécifiques Docker
        try {
            def dockerM3Exists = sh(
                script: 'ls /opt/maven 2>/dev/null || echo "not-found"',
                returnStdout: true
            ).trim()

            if (dockerM3Exists != "not-found") {
                echo "🐳 Détection: Jenkins dans Docker (Maven Docker trouvé)"
                return 'docker'
            }
        } catch (Exception e) {
            // Ignorer l'erreur
        }

        // Par défaut, considérer comme local
        echo "🖥️ Détection: Jenkins local"
        return 'local'

    } catch (Exception e) {
        echo "⚠️ Erreur de détection, utilisation par défaut: local (${e.getMessage()})"
        return 'local'
    }
}

def configureDockerEnvironment(jenkinsEnv, config) {
    def jenkinsConfig = config.jenkins[jenkinsEnv]

    try {
        if (jenkinsEnv == 'docker') {
            // Configuration spécifique pour Jenkins dans Docker
            sh """
                echo "🐳 Configuration Jenkins Docker:"
                # Vérifier l'accès au socket Docker
                if [ -S /var/run/docker.sock ]; then
                    echo "✅ Socket Docker accessible"
                    ls -la /var/run/docker.sock
                else
                    echo "❌ Socket Docker non accessible"
                fi

                # Vérifier les outils Maven et JDK
                echo "📋 Vérification des outils:"
                if [ -d "/opt/maven" ]; then
                    echo "✅ Maven Docker trouvé: /opt/maven"
                    ls -la /opt/maven/bin/mvn 2>/dev/null || echo "❌ Binaire mvn non trouvé"
                else
                    echo "❌ Maven Docker non trouvé"
                fi

                if [ -d "/opt/java/openjdk" ]; then
                    echo "✅ JDK Docker trouvé: /opt/java/openjdk"
                    ls -la /opt/java/openjdk/bin/java 2>/dev/null || echo "❌ Binaire java non trouvé"
                else
                    echo "❌ JDK Docker non trouvé"
                fi
            """
        } else {
            // Configuration pour Jenkins local
            echo "🖥️ Configuration Jenkins local:"
            sh """
                echo "📋 Vérification des outils locaux:"
                which java || echo "❌ Java non trouvé dans PATH"
                which mvn || echo "❌ Maven non trouvé dans PATH"
                echo "JAVA_HOME actuel: \${JAVA_HOME:-'Non défini'}"
                echo "MAVEN_HOME actuel: \${MAVEN_HOME:-'Non défini'}"
            """
        }

        // Configuration commune
        env.DOCKER_HOST = jenkinsConfig.dockerHost

    } catch (Exception e) {
        echo "⚠️ Erreur lors de la configuration Docker: ${e.getMessage()}"
    }
}

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
            sh '''
                docker --version
                echo "🐳 Informations Docker:"
                docker info --format "{{.ServerVersion}}" 2>/dev/null || echo "Version non disponible"
            '''
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

def displayBuildInfo(config, jenkinsEnv) {
    def jenkinsConfig = config.jenkins[jenkinsEnv]

    echo """
    ╔══════════════════════════════════════════════════════════════════════════════╗
    ║                            CONFIGURATION BUILD                               ║
    ╠══════════════════════════════════════════════════════════════════════════════╣
    ║ 🏗️  Build #: ${env.BUILD_NUMBER}
    ║ 🌿 Branch: ${env.BRANCH_NAME}
    ║ 🖥️  Jenkins Env: ${jenkinsEnv.toUpperCase()}
    ║ ☕ JDK Tool: ${jenkinsConfig.jdkTool}
    ║ 📦 Maven Tool: ${jenkinsConfig.mavenTool}
    ║ 🐳 Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "❌ Indisponible"}
    ║ 🌍 Environnement: ${env.ENV_NAME}
    ║ 🚪 Port: ${env.HTTP_PORT}
    ║ 🏷️  Tag: ${env.CONTAINER_TAG}
    ║ 📧 Email: ${config.emailRecipients}
    ║ 🔍 SonarQube: ${jenkinsConfig.sonarUrl}
    ║ 🌐 Docker Network: ${jenkinsConfig.dockerNetwork}
    ╚══════════════════════════════════════════════════════════════════════════════╝
    """
}

def validateDockerPrerequisites() {
    if (env.DOCKER_AVAILABLE != "true") {
        error "🚫 Docker n'est pas disponible. Impossible de continuer avec les étapes Docker."
    }

    if (!fileExists('Dockerfile')) {
        error "🚫 Fichier Dockerfile introuvable à la racine du projet."
    }

    def jarFiles = findFiles(glob: 'target/*.jar').findAll {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }

    if (jarFiles.length == 0) {
        error "🚫 Aucun fichier JAR exécutable trouvé dans target/"
    }

    env.JAR_FILE = jarFiles[0].path
    echo "✅ JAR trouvé: ${env.JAR_FILE}"
}

def buildDockerImage(config, jenkinsEnv) {
    try {
        echo "🏗️ Construction de l'image Docker sur Jenkins ${jenkinsEnv}..."

        def buildArgs = [
            "--pull",
            "--no-cache",
            "--build-arg JAR_FILE=${env.JAR_FILE}",
            "--build-arg BUILD_DATE=\"\$(date -u +'%Y-%m-%dT%H:%M:%SZ')\"",
            "--build-arg VCS_REF=\"\$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')\"",
            "--build-arg BUILD_NUMBER=\"${env.BUILD_NUMBER}\"",
            "--build-arg JENKINS_ENV=\"${jenkinsEnv}\"",
            "--label \"org.opencontainers.image.created=\$(date -u +'%Y-%m-%dT%H:%M:%SZ')\"",
            "--label \"org.opencontainers.image.revision=\$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')\"",
            "--label \"org.opencontainers.image.version=${env.CONTAINER_TAG}\"",
            "--label \"jenkins.environment=${jenkinsEnv}\"",
            "-t \"${config.containerName}:${env.CONTAINER_TAG}\"",
            "."
        ]

        sh "docker build ${buildArgs.join(' ')}"

        echo "✅ Image Docker construite avec succès"

        // Vérification de l'image
        sh "docker images ${config.containerName}:${env.CONTAINER_TAG}"

    } catch (Exception e) {
        error "🚫 Échec de la construction Docker: ${e.getMessage()}"
    }
}

def pushDockerImage(config) {
    try {
        withCredentials([usernamePassword(
            credentialsId: 'dockerhub-credentials',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASSWORD'
        )]) {

            echo "🚀 Connexion au registre Docker..."
            sh """
                echo "\${DOCKER_PASSWORD}" | docker login -u "\${DOCKER_USER}" --password-stdin ${config.dockerRegistry}
            """

            echo "🏷️ Tagging de l'image..."
            sh """
                docker tag "${config.containerName}:${env.CONTAINER_TAG}" "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
            """

            echo "📤 Push de l'image..."
            sh """
                docker push "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
            """

            // Tag latest pour master
            if (env.BRANCH_NAME == 'master') {
                echo "🏷️ Tagging latest pour master..."
                sh """
                    docker tag "${config.containerName}:${env.CONTAINER_TAG}" "\${DOCKER_USER}/${config.containerName}:latest"
                    docker push "\${DOCKER_USER}/${config.containerName}:latest"
                """
            }

            echo "🔒 Déconnexion du registre..."
            sh "docker logout ${config.dockerRegistry}"

            echo "✅ Image poussée avec succès"
        }
    } catch (Exception e) {
        error "🚫 Échec du push Docker: ${e.getMessage()}"
    }
}

def deployApplication(config, jenkinsEnv) {
    try {
        def jenkinsConfig = config.jenkins[jenkinsEnv]

        withCredentials([usernamePassword(
            credentialsId: 'dockerhub-credentials',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASSWORD'
        )]) {

            echo "🛑 Arrêt du conteneur existant..."
            sh """
                docker stop ${config.containerName} 2>/dev/null || echo "Conteneur non trouvé"
                docker rm ${config.containerName} 2>/dev/null || echo "Conteneur non trouvé"
            """

            def networkParam = ""
            if (jenkinsEnv == 'docker' && jenkinsConfig.dockerNetwork != 'host') {
                // Créer le réseau s'il n'existe pas
                sh """
                    docker network create ${jenkinsConfig.dockerNetwork} 2>/dev/null || echo "Réseau déjà existant"
                """
                networkParam = "--network ${jenkinsConfig.dockerNetwork}"
            } else if (jenkinsEnv == 'local') {
                networkParam = "--network host"
            }

            echo "🚀 Démarrage du nouveau conteneur sur Jenkins ${jenkinsEnv}..."
            sh """
                docker run -d \
                    --name "${config.containerName}" \
                    --restart unless-stopped \
                    ${networkParam} \
                    -p "${env.HTTP_PORT}:8080" \
                    -e "SPRING_PROFILES_ACTIVE=${env.ENV_NAME}" \
                    -e "SERVER_PORT=8080" \
                    -e "JAVA_OPTS=-Xmx512m -Xms256m" \
                    -e "JENKINS_DEPLOY_ENV=${jenkinsEnv}" \
                    "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
            """

            echo "✅ Conteneur démarré avec succès sur Jenkins ${jenkinsEnv}"
        }
    } catch (Exception e) {
        error "🚫 Échec du déploiement: ${e.getMessage()}"
    }
}

def performHealthCheck(config, jenkinsEnv) {
    try {
        echo "🩺 Vérification de la santé de l'application sur Jenkins ${jenkinsEnv}..."

        // Attendre que le conteneur soit en cours d'exécution
        timeout(time: config.timeouts.deployment, unit: 'MINUTES') {
            waitUntil {
                script {
                    def status = sh(
                        script: "docker inspect -f '{{.State.Status}}' ${config.containerName} 2>/dev/null || echo 'not-found'",
                        returnStdout: true
                    ).trim()

                    echo "Status du conteneur: ${status}"

                    if (status == "running") {
                        return true
                    } else if (status == "exited") {
                        sh "docker logs ${config.containerName} --tail 50"
                        error "❌ Le conteneur s'est arrêté de manière inattendue"
                    }

                    sleep(10)
                    return false
                }
            }
        }

        // Attendre que l'application soit prête
        echo "⏳ Attente du démarrage de l'application..."
        sleep(30)

        // Test HTTP avec URL adaptée selon l'environnement
        def healthUrl = getHealthCheckUrl(jenkinsEnv, env.HTTP_PORT)
        echo "🔍 Test de santé sur: ${healthUrl}"

        timeout(time: 2, unit: 'MINUTES') {
            waitUntil {
                script {
                    def exitCode = sh(
                        script: "curl -f -s ${healthUrl} > /dev/null",
                        returnStatus: true
                    )

                    if (exitCode == 0) {
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

        echo "✅ Application en bonne santé et accessible sur Jenkins ${jenkinsEnv}"

    } catch (Exception e) {
        // Logs pour debug
        sh "docker logs ${config.containerName} --tail 100 || echo 'Impossible de récupérer les logs'"
        error "🚫 Health check échoué: ${e.getMessage()}"
    }
}

def getHealthCheckUrl(jenkinsEnv, port) {
    if (jenkinsEnv == 'docker') {
        // Dans Docker, utiliser le nom du conteneur ou localhost selon la configuration réseau
        return "http://localhost:${port}/actuator/health"
    } else {
        // En local, utiliser localhost
        return "http://localhost:${port}/actuator/health"
    }
}

def cleanupDockerImages(config) {
    try {
        if (env.DOCKER_AVAILABLE == "true") {
            echo "🧹 Nettoyage des images Docker..."
            sh """
                # Suppression des images non taguées
                docker image prune -f || true

                # Garde seulement les 3 dernières versions de notre image
                docker images "${config.containerName}" --format "{{.Repository}}:{{.Tag}}" | \
                head -n -3 | xargs -r docker rmi || true
            """
        }
    } catch (Exception e) {
        echo "⚠️ Erreur lors du nettoyage Docker: ${e.getMessage()}"
    }
}

def sendNotification(recipients, jenkinsEnv) {
    try {
        def cause = currentBuild.getBuildCauses()?.collect { it.shortDescription }?.join(', ') ?: "Non spécifiée"
        def duration = currentBuild.durationString.replace(' and counting', '')
        def status = currentBuild.currentResult ?: 'SUCCESS'

        def statusIcon = [
            'SUCCESS': '✅',
            'FAILURE': '❌',
            'UNSTABLE': '⚠️',
            'ABORTED': '🛑'
        ][status] ?: '❓'

        def subject = "${statusIcon} [Jenkins-${jenkinsEnv.toUpperCase()}] ${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - ${status}"

        def body = """
        ${statusIcon} Résultat: ${status}

        📊 Détails du Build:
        • Projet: ${env.JOB_NAME}
        • Build: #${env.BUILD_NUMBER}
        • Branche: ${env.BRANCH_NAME ?: 'N/A'}
        • Durée: ${duration}
        • Environnement: ${env.ENV_NAME}
        • Port: ${env.HTTP_PORT}
        • Jenkins: ${jenkinsEnv.toUpperCase()}

        🔗 Liens:
        • Console: ${env.BUILD_URL}console
        • Artefacts: ${env.BUILD_URL}artifact/

        🐳 Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "❌ Indisponible"}
        🚀 Cause: ${cause}

        ${status == 'SUCCESS' ? '🎉 Déploiement réussi!' : '🔍 Vérifiez les logs pour plus de détails.'}
        """

        mail(
            to: recipients,
            subject: subject,
            body: body,
            mimeType: 'text/plain'
        )

        echo "📧 Email de notification envoyé à: ${recipients}"

    } catch (Exception e) {
        echo "⚠️ Échec de l'envoi d'email: ${e.getMessage()}"
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