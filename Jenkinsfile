// Configuration centralisée
def config = [
    emailRecipients: "magassakara@gmail.com",
    containerName: "tourguide-app",
    dockerRegistry: "docker.io",
    dockerHome: '/usr/local/bin',
    sonarProjectKey: "tourguide",
    sonar: [
        communityEdition: true,
        projectKey: "tourguide",
        qualityProfileJava: "Sonar way",
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
        securityAudit: 10
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

        stage('Install Dependencies') {
            steps {
                script {
                    echo "📦 Installing local dependencies..."
                    sh """
                        mvn install:install-file \
                           -Dfile=libs/gpsUtil.jar \
                           -DgroupId=gpsUtil \
                           -DartifactId=gpsUtil \
                           -Dversion=1.0.0 \
                           -Dpackaging=jar \
                           -DlocalRepositoryPath=${WORKSPACE}/.m2/repository

                        mvn install:install-file \
                           -Dfile=libs/TripPricer.jar \
                           -DgroupId=tripPricer \
                           -DartifactId=tripPricer \
                           -Dversion=1.0.0 \
                           -Dpackaging=jar \
                           -DlocalRepositoryPath=${WORKSPACE}/.m2/repository

                        mvn install:install-file \
                           -Dfile=libs/rewardCentral.jar \
                           -DgroupId=rewardCentral \
                           -DartifactId=rewardCentral \
                           -Dversion=1.0.0 \
                           -Dpackaging=jar \
                           -DlocalRepositoryPath=${WORKSPACE}/.m2/repository
                    """
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    sh """
                        mvn clean verify \
                            org.jacoco:jacoco-maven-plugin:prepare-agent \
                            -DskipTests=false \
                            -Dmaven.test.failure.ignore=false \
                            -Djacoco.destFile=target/jacoco.exec \
                            -Djacoco.dataFile=target/jacoco.exec \
                            -B -U
                    """
                }
            }
            post {
                always {
                    junit 'target/surefire-reports/TEST-*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Code Analysis') {
            when {
                anyOf { branch 'master'; branch 'develop'; changeRequest() }
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: 'sonartoken', variable: 'SONAR_TOKEN')]) {
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} \
                                -Dsonar.host.url=\$SONAR_HOST_URL \
                                -Dsonar.token=\${SONAR_TOKEN} \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.exclusions="${config.sonar.exclusions.join(',')}" \
                                -Dsonar.java.source=21 \
                                -Dsonar.java.target=21 \
                                -B
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            when {
                allOf {
                    anyOf { branch 'master'; branch 'develop'; changeRequest() }
                    expression { return fileExists('.scannerwork/report-task.txt') }
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
                allOf {
                    anyOf { branch 'master'; branch 'develop' }
                    expression { return env.DOCKER_AVAILABLE == "true" }
                }
            }
            steps {
                script {
                    validateDockerPrerequisites()
                    buildDockerImage(config)
                }
            }
        }

        stage('Docker Push') {
            when {
                allOf {
                    anyOf { branch 'master'; branch 'develop' }
                    expression { return env.DOCKER_AVAILABLE == "true" }
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
                allOf {
                    anyOf { branch 'master'; branch 'develop' }
                    expression { return env.DOCKER_AVAILABLE == "true" }
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
                    anyOf { branch 'master'; branch 'develop' }
                    expression { return env.DOCKER_AVAILABLE == "true" }
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
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                if (env.DOCKER_AVAILABLE == "true") {
                    cleanupDockerImages(config)
                }
                cleanWs()
            }
        }
        success {
            script {
                sendNotification("${config.emailRecipients}", "SUCCESS")
            }
        }
        failure {
            script {
                sendNotification("${config.emailRecipients}", "FAILURE")
            }
        }
        unstable {
            script {
                sendNotification("${config.emailRecipients}", "UNSTABLE")
            }
        }
    }
}

// Fonctions utilitaires
def validateEnvironment() {
    echo "Validating environment..."
    try {
        sh "mvn --version"
        sh "java --version"
        sh "docker --version"
    } catch (Exception e) {
        error "Required tools not found: ${e.getMessage()}"
    }
}

def checkDockerAvailability() {
    try {
        return sh(script: 'docker info >/dev/null 2>&1 && echo "true" || echo "false"', returnStdout: true).trim()
    } catch (Exception e) {
        return "false"
    }
}

def displayBuildInfo(config) {
    echo """
    =========================================
    BUILD CONFIGURATION
    Branch: ${env.BRANCH_NAME}
    Environment: ${env.ENV_NAME}
    Port: ${env.HTTP_PORT}
    Docker: ${env.DOCKER_AVAILABLE}
    =========================================
    """
}

def validateDockerPrerequisites() {
    if (!fileExists('Dockerfile')) {
        error "Dockerfile not found"
    }
    def jarFile = findFiles(glob: 'target/*.jar').find {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }
    if (!jarFile) {
        error "No executable JAR found in target/"
    }
    env.JAR_FILE = jarFile.path
}

def buildDockerImage(config) {
    sh """
        docker build \
            --build-arg JAR_FILE=${env.JAR_FILE} \
            -t ${config.containerName}:${env.CONTAINER_TAG} .
    """
}

def pushDockerImage(config) {
    withCredentials([usernamePassword(
        credentialsId: 'dockerhub-credentials',
        usernameVariable: 'DOCKER_USER',
        passwordVariable: 'DOCKER_PASSWORD'
    )]) {
        sh """
            echo "\${DOCKER_PASSWORD}" | docker login -u "\${DOCKER_USER}" --password-stdin
            docker tag ${config.containerName}:${env.CONTAINER_TAG} \${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}
            docker push \${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}
            if [ "${env.BRANCH_NAME}" = "master" ]; then
                docker tag ${config.containerName}:${env.CONTAINER_TAG} \${DOCKER_USER}/${config.containerName}:latest
                docker push \${DOCKER_USER}/${config.containerName}:latest
            fi
            docker logout
        """
    }
}

def deployApplication(config) {
    sh """
        docker stop ${config.containerName} || true
        docker rm ${config.containerName} || true
        docker run -d \
            --name ${config.containerName} \
            -p ${env.HTTP_PORT}:8080 \
            -e SPRING_PROFILES_ACTIVE=${env.ENV_NAME} \
            \${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}
    """
}

def performHealthCheck(config) {
    timeout(time: config.timeouts.deployment, unit: 'MINUTES') {
        waitUntil {
            def status = sh(script: "curl -s -o /dev/null -w '%{http_code}' http://localhost:${env.HTTP_PORT}/actuator/health || echo '000'", returnStdout: true).trim()
            return status == "200"
        }
    }
}

def cleanupDockerImages(config) {
    sh """
        docker image prune -f
        docker images --filter=reference="${config.containerName}:*" --format '{{.Repository}}:{{.Tag}}' | \
        head -n -3 | xargs -r docker rmi || true
    """
}

def sendNotification(recipients, status) {
    def subject = "[${status}] ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def body = """
        Status: ${status}
        Branch: ${env.BRANCH_NAME}
        Build: ${env.BUILD_URL}
        Duration: ${currentBuild.durationString.replace(' and counting', '')}
    """
    mail(to: recipients, subject: subject, body: body)
}

// Helper functions
String getHTTPPort(branchName, ports) {
    def branch = branchName?.toLowerCase()
    return ports[branch] ?: ports.default
}

String getEnvName(branchName, environments) {
    def branch = branchName?.toLowerCase()
    return environments[branch] ?: environments.default
}

String getTag(buildNumber, branchName) {
    def safeBranch = (branchName ?: "unknown").replaceAll('[^a-zA-Z0-9-]', '-').toLowerCase()
    return (safeBranch == 'master') ? "${buildNumber}-stable" : "${buildNumber}-${safeBranch}-snapshot"
}

String getSonarProjectKey(branchName, sonarConfig) {
    return sonarConfig.communityEdition ? sonarConfig.projectKey : "${sonarConfig.projectKey}-${branchName?.toLowerCase()}"
}