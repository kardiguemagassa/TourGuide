// Configuration centralisée adaptable aux deux environnements
def config = [
    // Paramètres généraux
    emailRecipients: "magassakara@gmail.com",
    containerName: "tourguide-app",

    // Configuration Docker - adaptable selon l'environnement
    docker: [
        local: [
            registry: "",
            home: '/usr/local/bin',
            useDocker: true,
            useDockerBuildx: false,
            cleanImages: true
        ],
        dockerized: [
            registry: "docker.io",
            home: '/usr/bin',
            useDocker: true,
            useDockerBuildx: true,
            cleanImages: false  // Dans Docker, éviter de nettoyer pour ne pas supprimer des images utiles
        ]
    ],

    // Configuration SonarQube
    sonar: [
        projectKey: "tourguide",
        qualityProfileJava: "Sonar way",
        exclusions: [
            "**/target/**",
            "**/*.min.js",
            "**/node_modules/**",
            "**/.mvn/**"
        ]
    ],

    // Timeouts
    timeouts: [
        qualityGate: 2,
        deployment: 5,
        sonarAnalysis: 10,
        securityAudit: 10
    ],

    // Ports et environnements par branche
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
        // Variables communes
        BRANCH_NAME = "${env.BRANCH_NAME ?: 'unknown'}"
        BUILD_NUMBER = "${env.BUILD_NUMBER ?: '0'}"
        HTTP_PORT = "${getHTTPPort(env.BRANCH_NAME, config.ports)}"
        ENV_NAME = "${getEnvName(env.BRANCH_NAME, config.environments)}"
        CONTAINER_TAG = "${getTag(env.BUILD_NUMBER, env.BRANCH_NAME)}"
        SONAR_PROJECT_KEY = "${config.sonar.projectKey}"
        MAVEN_OPTS = "-Dmaven.repo.local=${WORKSPACE}/.m2/repository -Xmx1024m"

        // Variables déterminées dynamiquement
        IS_DOCKERIZED_JENKINS = "${isDockerizedJenkins()}"
        DOCKER_CONFIG = "${IS_DOCKERIZED_JENKINS == 'true' ? 'dockerized' : 'local'}"
    }

    stages {
        stage('Initialisation') {
            steps {
                script {
                    // Détection de l'environnement Jenkins
                    detectJenkinsEnvironment()

                    // Affichage des informations de configuration
                    displayBuildInfo(config)

                    // Checkout du code
                    checkout scm

                    // Vérification des prérequis
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
                    expression { fileExists('.scannerwork/report-task.txt') }
                }
            }
            steps {
                script {
                    checkQualityGate(config)
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
                    expression { config.docker[DOCKER_CONFIG].useDocker }
                }
            }
            parallel {
                stage('Docker Build') {
                    steps {
                        script {
                            buildDockerImage(config)
                        }
                    }
                }

                stage('Docker Push') {
                    when {
                        expression { env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'develop' }
                    }
                    steps {
                        script {
                            pushDockerImage(config)
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
                    expression { config.docker[DOCKER_CONFIG].useDocker }
                }
            }
            steps {
                script {
                    deployApplication(config)
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
                    expression { config.docker[DOCKER_CONFIG].useDocker }
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
                postBuildActions(config)
            }
        }
        failure {
            script {
                sendNotification(config.emailRecipients, "FAILURE")
            }
        }
        success {
            script {
                sendNotification(config.emailRecipients, "SUCCESS")
            }
        }
        unstable {
            script {
                sendNotification(config.emailRecipients, "UNSTABLE")
            }
        }
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES
// =============================================================================

def detectJenkinsEnvironment() {
    // Détection si Jenkins tourne dans un conteneur Docker
    try {
        def isDocker = sh(script: 'grep -q docker /proc/1/cgroup 2>/dev/null', returnStatus: true) == 0
        env.IS_DOCKERIZED_JENKINS = isDocker ? "true" : "false"

        echo "Environnement Jenkins détecté : ${isDocker ? 'Docker' : 'Local'}"
        echo "Configuration Docker utilisée : ${env.DOCKER_CONFIG}"
    } catch (Exception e) {
        echo "Impossible de détecter l'environnement Jenkins : ${e.getMessage()}"
        env.IS_DOCKERIZED_JENKINS = "false"
        env.DOCKER_CONFIG = "local"
    }
}

def displayBuildInfo(config) {
    def dockerConfig = config.docker[DOCKER_CONFIG]

    echo """
    ================================================================================
                            CONFIGURATION BUILD
    ================================================================================
     Environnement Jenkins : ${env.IS_DOCKERIZED_JENKINS == 'true' ? 'Docker' : 'Local'}
     Build #: ${env.BUILD_NUMBER}
     Branch: ${env.BRANCH_NAME}
     Java: ${env.JAVA_HOME}
     Maven: ${env.MAVEN_HOME}
     Docker: ${dockerConfig.useDocker ? 'Activé' : 'Désactivé'}
     Environnement: ${env.ENV_NAME}
     Port: ${env.HTTP_PORT}
     Tag: ${env.CONTAINER_TAG}
     Email: ${config.emailRecipients}
     Projet SonarQube: ${env.SONAR_PROJECT_KEY}
     Registry Docker: ${dockerConfig.registry ?: 'Aucun'}
    ================================================================================
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

    // Vérification Docker si nécessaire
    if (config.docker[DOCKER_CONFIG].useDocker) {
        def dockerAvailable = checkDockerAvailability()
        if (!dockerAvailable) {
            if (env.IS_DOCKERIZED_JENKINS == 'true') {
                error "Docker est requis mais non disponible dans le conteneur Jenkins"
            } else {
                echo "Attention: Docker n'est pas disponible, certaines étapes seront ignorées"
            }
        }
    }

    // Vérification de l'espace disque
    sh """
        df -h . | tail -1 | awk '{print "Espace disque disponible: " \$4 " (" \$5 " utilisé)"}'
    """
}

def checkDockerAvailability() {
    try {
        def result = sh(
            script: '''
                # Vérification avec retry
                for i in 1 2 3; do
                    if command -v docker >/dev/null 2>&1; then
                        if docker info >/dev/null 2>&1; then
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

        env.DOCKER_AVAILABLE = result
        return result == "true"
    } catch (Exception e) {
        echo "Erreur lors de la vérification Docker: ${e.getMessage()}"
        env.DOCKER_AVAILABLE = "false"
        return false
    }
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

def performSonarAnalysis(config) {
    echo "Démarrage de l'analyse SonarQube..."

    withSonarQubeEnv('SonarQube') {
        withCredentials([string(credentialsId: 'sonartoken', variable: 'SONAR_TOKEN')]) {
            def sonarCmd = """
                mvn sonar:sonar \
                    -Dsonar.projectKey=${config.sonar.projectKey} \
                    -Dsonar.host.url=\$SONAR_HOST_URL \
                    -Dsonar.token=\$SONAR_TOKEN \
                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                    -Dsonar.java.binaries=target/classes \
                    -Dsonar.exclusions="${config.sonar.exclusions.join(',')}" \
                    -Dsonar.java.source=21 \
                    -Dsonar.java.target=21 \
                    -B -q
            """

            if (env.BRANCH_NAME && env.BRANCH_NAME != 'master') {
                sonarCmd += " -Dsonar.branch.name=${env.BRANCH_NAME}"
            }

            sh sonarCmd
        }
    }
}

def checkQualityGate(config) {
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
    echo "Audit de sécurité Maven..."

    timeout(time: config.timeouts.securityAudit, unit: 'MINUTES') {
        sh """
            mvn versions:display-dependency-updates \
                -DprocessDependencyManagement=false \
                -DgenerateBackupPoms=false \
                -B -q

            mvn versions:display-plugin-updates \
                -DgenerateBackupPoms=false \
                -B -q
        """
    }
}

def buildDockerImage(config) {
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker non disponible, skip de l'étape de build"
        return
    }

    def dockerConfig = config.docker[DOCKER_CONFIG]

    // Trouver le fichier JAR
    def jarFile = findFiles(glob: 'target/*.jar').find {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }?.path

    if (!jarFile) {
        error "Aucun fichier JAR exécutable trouvé dans target/"
    }

    echo "Construction de l'image Docker avec ${jarFile}..."

    try {
        def buildCmd = """
            docker build \
                --pull \
                --no-cache \
                --build-arg JAR_FILE=${jarFile} \
                -t "${config.containerName}:${env.CONTAINER_TAG}" \
                .
        """

        if (dockerConfig.useDockerBuildx) {
            buildCmd = """
                docker buildx build \
                    --pull \
                    --no-cache \
                    --build-arg JAR_FILE=${jarFile} \
                    -t "${config.containerName}:${env.CONTAINER_TAG}" \
                    --load \
                    .
            """
        }

        sh buildCmd
        echo "Image Docker construite avec succès"
    } catch (Exception e) {
        error "Échec de la construction Docker: ${e.getMessage()}"
    }
}

def pushDockerImage(config) {
    if (env.DOCKER_AVAILABLE != "true" || !config.docker[DOCKER_CONFIG].registry) {
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
                echo "\${DOCKER_PASSWORD}" | docker login -u "\${DOCKER_USER}" --password-stdin ${config.docker[DOCKER_CONFIG].registry}
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
            sh "docker logout ${config.docker[DOCKER_CONFIG].registry}"

            echo "Image poussée avec succès"
        } catch (Exception e) {
            error "Échec du push Docker: ${e.getMessage()}"
        }
    }
}

def deployApplication(config) {
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker non disponible, skip du déploiement"
        return
    }

    try {
        echo "Déploiement de l'application..."

        // Arrêt du conteneur existant
        sh """
            docker stop ${config.containerName} 2>/dev/null || echo "Aucun conteneur à arrêter"
            docker rm ${config.containerName} 2>/dev/null || echo "Aucun conteneur à supprimer"
        """

        // Démarrage du nouveau conteneur
        def imageName = config.docker[DOCKER_CONFIG].registry ?
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
    } catch (Exception e) {
        error "Échec du déploiement: ${e.getMessage()}"
    }
}

def performHealthCheck(config) {
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

def postBuildActions(config) {
    // Archivage des artefacts
    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true

    // Nettoyage Docker si configuré
    if (env.DOCKER_AVAILABLE == "true" && config.docker[DOCKER_CONFIG].cleanImages) {
        sh """
            docker system prune -f 2>/dev/null || true
        """
    }

    // Nettoyage du workspace
    cleanWs()
}

def sendNotification(recipients, status) {
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
        to: recipients,
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

Boolean isDockerizedJenkins() {
    try {
        // Vérifie si on est dans un conteneur Docker
        return sh(script: 'grep -q docker /proc/1/cgroup 2>/dev/null', returnStatus: true) == 0
    } catch (Exception e) {
        echo "Impossible de déterminer si Jenkins est dans Docker: ${e.getMessage()}"
        return false
    }
}