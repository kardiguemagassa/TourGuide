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
            deployment: 5,
            securityAudit: 3
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
        CONFIG = getConfig()
        IS_DOCKERIZED = sh(script: 'if grep -q docker /proc/1/cgroup 2>/dev/null; then echo "true"; else echo "false"; fi', returnStdout: true).trim()
        MAVEN_HOME = "${IS_DOCKERIZED == 'true' ? '/usr/share/maven' : tool('Docker-M3')}"
        JAVA_HOME = "${IS_DOCKERIZED == 'true' ? '/usr/lib/jvm/java-17-openjdk-amd64' : tool('Docker-JDK-17')}"
        PATH = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${env.PATH}"
        DOCKER_BUILDKIT = "1"
        COMPOSE_DOCKER_CLI_BUILD = "1"
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
                    try {
                        checkout scm
                        env.DOCKER_AVAILABLE = checkDockerAvailability()
                        displayBuildInfo()
                        validateEnvironment()
                    } catch (Exception e) {
                        error "Initialization failed: ${e.getMessage()}"
                    }
                }
            }
        }

        stage('Verify Local JARs') {
            steps {
                script {
                    try {
                        verifyRequiredJars()
                    } catch (Exception e) {
                        error "Missing required JAR files: ${e.getMessage()}"
                    }
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                script {
                    try {
                        installLocalDependencies()
                    } catch (Exception e) {
                        error "Dependency installation failed: ${e.getMessage()}"
                    }
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    try {
                        runMavenBuild()
                    } catch (Exception e) {
                        error "Maven build failed: ${e.getMessage()}"
                    }
                }
            }
            post {
                always {
                    script {
                        try {
                            publishTestAndCoverageResults()
                        } catch (Exception e) {
                            echo "Failed to publish results: ${e.getMessage()}"
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
                    try {
                        performSonarAnalysis()
                    } catch (Exception e) {
                        error "SonarQube analysis failed: ${e.getMessage()}"
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
                script {
                    try {
                        checkQualityGate()
                    } catch (Exception e) {
                        error "Quality Gate check failed: ${e.getMessage()}"
                    }
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
                    try {
                        runMavenSecurityAudit()
                    } catch (Exception e) {
                        echo "Security audit failed: ${e.getMessage()}"
                        currentBuild.result = 'UNSTABLE'
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
                    expression { env.DOCKER_AVAILABLE == 'true' }
                }
            }
            parallel {
                stage('Docker Build') {
                    steps {
                        script {
                            try {
                                buildDockerImage()
                            } catch (Exception e) {
                                error "Docker build failed: ${e.getMessage()}"
                            }
                        }
                    }
                }

                stage('Docker Push') {
                    when {
                        expression { env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'develop' }
                    }
                    steps {
                        script {
                            try {
                                pushDockerImage()
                            } catch (Exception e) {
                                error "Docker push failed: ${e.getMessage()}"
                            }
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
                    try {
                        deployApplication()
                    } catch (Exception e) {
                        error "Deployment failed: ${e.getMessage()}"
                    }
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
                    try {
                        performHealthCheck()
                    } catch (Exception e) {
                        error "Health check failed: ${e.getMessage()}"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                try {
                    postBuildActions()
                } catch (Exception e) {
                    echo "Post-build actions failed: ${e.getMessage()}"
                }
            }
        }
        failure {
            script {
                try {
                    sendNotification("FAILURE")
                } catch (Exception e) {
                    echo "Failed to send failure notification: ${e.getMessage()}"
                }
            }
        }
        success {
            script {
                try {
                    sendNotification("SUCCESS")
                } catch (Exception e) {
                    echo "Failed to send success notification: ${e.getMessage()}"
                }
            }
        }
        unstable {
            script {
                try {
                    sendNotification("UNSTABLE")
                } catch (Exception e) {
                    echo "Failed to send unstable notification: ${e.getMessage()}"
                }
            }
        }
    }
}

// Utility Functions
def verifyRequiredJars() {
    echo "🔍 Vérification des fichiers JAR requis..."
    def requiredJars = [
        'gpsUtil.jar',
        'tripPricer.jar',
        'rewardCentral.jar'
    ]

    requiredJars.each { jarName ->
        if (!fileExists("libs/${jarName}")) {
            error "Fichier JAR manquant: libs/${jarName}"
        }
    }
    echo "✅ Tous les fichiers JAR requis sont présents"
}

def installLocalDependencies() {
    echo "📦 Installation des dépendances locales..."
    try {
        sh '''
            echo "Installation de gpsUtil.jar..."
            mvn install:install-file \
                -Dfile=libs/gpsUtil.jar \
                -DgroupId=gpsUtil \
                -DartifactId=gpsUtil \
                -Dversion=1.0.0 \
                -Dpackaging=jar \
                -DgeneratePom=true \
                -Dmaven.repo.local=${WORKSPACE}/.m2/repository

            echo "Installation de tripPricer.jar..."
            mvn install:install-file \
                -Dfile=libs/tripPricer.jar \
                -DgroupId=tripPricer \
                -DartifactId=tripPricer \
                -Dversion=1.0.0 \
                -Dpackaging=jar \
                -DgeneratePom=true \
                -Dmaven.repo.local=${WORKSPACE}/.m2/repository

            echo "Installation de rewardCentral.jar..."
            mvn install:install-file \
                -Dfile=libs/rewardCentral.jar \
                -DgroupId=rewardCentral \
                -DartifactId=rewardCentral \
                -Dversion=1.0.0 \
                -Dpackaging=jar \
                -DgeneratePom=true \
                -Dmaven.repo.local=${WORKSPACE}/.m2/repository
        '''
        echo "✅ Dépendances locales installées avec succès"
    } catch (Exception e) {
        error "Échec de l'installation des dépendances locales: ${e.getMessage()}"
    }
}

// [Les autres fonctions restent identiques à celles du code précédent...]

// Configuration utility functions
String getEnvName(String branchName, Map environments) {
    def branch = branchName?.toLowerCase() ?: 'default'
    return environments.get(branch, environments.default)
}

String getHTTPPort(String branchName, Map ports) {
    def branch = branchName?.toLowerCase() ?: 'default'
    return ports.get(branch, ports.default)
}

String getTag(String buildNumber, String branchName) {
    def safeBranch = (branchName ?: "unknown")
        .replaceAll('[^a-zA-Z0-9-]', '-')
        .toLowerCase()

    return (safeBranch == 'master') ?
        "${buildNumber}-stable" :
        "${buildNumber}-${safeBranch}-snapshot"
}