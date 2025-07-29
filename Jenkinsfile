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
def checkDockerAvailability() {
    try {
        def result = sh(
            script: '''
                for i in 1 2 3; do
                    if command -v docker >/dev/null 2>&1; then
                        if timeout 10 docker info >/dev/null 2>&1; then
                            echo "true"
                            exit 0
                        fi
                    fi
                    echo "Attempt $i/3 failed, retrying in 5s..."
                    sleep 5
                done
                echo "false"
            ''',
            returnStdout: true
        ).trim()

        if (result == "true") {
            echo "✅ Docker available and functional"
            sh 'docker --version'
            sh 'docker info'
        } else {
            echo "❌ Docker not available or not functional"
        }
        return result
    } catch (Exception e) {
        echo "❌ Docker check error: ${e.getMessage()}"
        return "false"
    }
}

def displayBuildInfo() {
    def config = getConfig()
    echo """
    ╔══════════════════════════════════════════════════════════════════════════════╗
    ║                            BUILD CONFIGURATION                               ║
    ╠══════════════════════════════════════════════════════════════════════════════╣
    ║ 🏗️  Build #: ${env.BUILD_NUMBER}
    ║ 🌿 Branch: ${env.BRANCH_NAME}
    ║ ☕ Java: ${env.JAVA_HOME}
    ║ 📦 Maven: ${env.MAVEN_HOME}
    ║ 🐳 Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Available" : "❌ Unavailable"}
    ║ 🌍 Environment: ${env.ENV_NAME}
    ║ 🚪 Port: ${env.HTTP_PORT}
    ║ 🏷️  Tag: ${env.CONTAINER_TAG}
    ║ 📧 Email: ${config.emailRecipients}
    ╚══════════════════════════════════════════════════════════════════════════════╝
    """
}

def validateEnvironment() {
    echo "Validating environment..."
    try {
        def requiredTools = ['mvn', 'java', 'git']
        requiredTools.each { tool ->
            def toolPath = sh(script: "which ${tool} || echo 'not_found'", returnStdout: true).trim()
            if (toolPath == 'not_found') {
                error "${tool} not found in PATH"
            } else {
                echo "${tool} available at: ${toolPath}"
            }
        }

        sh """
            echo "=== DISK SPACE ==="
            df -h .
        """
    } catch (Exception e) {
        error "Environment validation failed: ${e.getMessage()}"
    }
}

def installLocalDependencies() {
    echo "📦 Installing local dependencies (libs/*.jar)..."
    try {
        sh '''
            if [ ! -d "libs" ]; then
                echo "libs directory not found"
                exit 0
            fi

            for jar in libs/*.jar; do
                if [ -f "$jar" ]; then
                    jar_name=$(basename "$jar" .jar)
                    echo "Installing $jar_name..."

                    mvn install:install-file \
                        -Dfile="$jar" \
                        -DgroupId=custom.lib \
                        -DartifactId="$jar_name" \
                        -Dversion=1.0.0 \
                        -Dpackaging=jar \
                        -Dmaven.repo.local=${WORKSPACE}/.m2/repository
                fi
            done
        '''
    } catch (Exception e) {
        error "Local dependency installation failed: ${e.getMessage()}"
    }
}

def runMavenBuild() {
    echo "Building and testing with Maven..."
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
        echo "Maven build error: ${e.getMessage()}"
        sh "mvn --version"
        sh "java -version"
        error "Maven build failed"
    }
}

def publishTestAndCoverageResults() {
    if (fileExists('target/surefire-reports/TEST-*.xml')) {
        junit 'target/surefire-reports/TEST-*.xml'
    }

    if (fileExists('target/site/jacoco/jacoco.xml')) {
        jacoco(
            execPattern: 'target/jacoco.exec',
            classPattern: 'target/classes',
            sourcePattern: 'src/main/java',
            exclusionPattern: 'src/test/*'
        )
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

def performSonarAnalysis() {
    def config = getConfig()
    echo "Starting SonarQube analysis..."
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
    echo "Checking Quality Gate..."
    timeout(time: config.timeouts.qualityGate, unit: 'MINUTES') {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
            echo "Quality Gate: ${qg.status}"
            if (qg.conditions) {
                qg.conditions.each { condition ->
                    echo "  • ${condition.metricName}: ${condition.actualValue} (threshold: ${condition.errorThreshold})"
                }
            }
            if (env.BRANCH_NAME == 'master') {
                error "Quality Gate failed on master"
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
    echo "Running Maven security audit..."
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
        echo "Docker not available, skipping build"
        return
    }

    def jarFile = findFiles(glob: 'target/*.jar').find {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }?.path

    if (!jarFile) {
        error "No executable JAR file found in target/"
    }

    echo "Building Docker image with ${jarFile}..."
    try {
        sh """
            docker build \
                --pull \
                --no-cache \
                --build-arg JAR_FILE=${jarFile} \
                -t "${config.containerName}:${env.CONTAINER_TAG}" \
                .
        """
        echo "Docker image built successfully"
    } catch (Exception e) {
        error "Docker build failed: ${e.getMessage()}"
    }
}

def pushDockerImage() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true" || !config.dockerRegistry) {
        echo "Docker not available or no registry configured, skipping push"
        return
    }

    withCredentials([usernamePassword(
        credentialsId: 'dockerhub-credentials',
        usernameVariable: 'DOCKER_USER',
        passwordVariable: 'DOCKER_PASSWORD'
    )]) {
        try {
            echo "Logging into Docker registry..."
            sh """
                echo "\${DOCKER_PASSWORD}" | docker login -u "\${DOCKER_USER}" --password-stdin ${config.dockerRegistry}
            """

            echo "Tagging and pushing image..."
            sh """
                docker tag "${config.containerName}:${env.CONTAINER_TAG}" "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
                docker push "\${DOCKER_USER}/${config.containerName}:${env.CONTAINER_TAG}"
            """

            if (env.BRANCH_NAME == 'master') {
                sh """
                    docker tag "${config.containerName}:${env.CONTAINER_TAG}" "\${DOCKER_USER}/${config.containerName}:latest"
                    docker push "\${DOCKER_USER}/${config.containerName}:latest"
                """
            }

            echo "Logging out from registry..."
            sh "docker logout ${config.dockerRegistry}"
            echo "Image pushed successfully"
        } catch (Exception e) {
            error "Docker push failed: ${e.getMessage()}"
        }
    }
}

def deployApplication() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker not available, skipping deployment"
        return
    }

    try {
        withCredentials([usernamePassword(
            credentialsId: 'dockerhub-credentials',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASSWORD'
        )]) {
            echo "Stopping existing container..."
            sh """
                docker stop ${config.containerName} 2>/dev/null || echo "No container to stop"
                docker rm ${config.containerName} 2>/dev/null || echo "No container to remove"
            """

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
            echo "Application deployed successfully on port ${env.HTTP_PORT}"
        }
    } catch (Exception e) {
        error "Deployment failed: ${e.getMessage()}"
    }
}

def performHealthCheck() {
    def config = getConfig()
    if (env.DOCKER_AVAILABLE != "true") {
        echo "Docker not available, skipping health check"
        return
    }

    try {
        echo "Checking application health..."
        timeout(time: config.timeouts.deployment, unit: 'MINUTES') {
            waitUntil {
                def health = sh(
                    script: "curl -s http://localhost:${env.HTTP_PORT}/actuator/health | grep -q '\"status\":\"UP\"'",
                    returnStatus: true
                )

                if (health == 0) {
                    echo "Application healthy"
                    return true
                } else {
                    echo "Waiting for application to start..."
                    sleep(10)
                    return false
                }
            }
        }
    } catch (Exception e) {
        error "Health check failed: ${e.getMessage()}"
    }
}

def postBuildActions() {
    def config = getConfig()
    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true

    if (env.DOCKER_AVAILABLE == "true") {
        sh """
            docker system prune -f 2>/dev/null || true
        """
    }
    cleanWs()
}

def sendNotification(status) {
    def config = getConfig()
    def subject = "[Jenkins] ${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - ${status}"
    def body = """
        Build result: ${status}

        Details:
        - Project: ${env.JOB_NAME}
        - Build: #${env.BUILD_NUMBER}
        - Branch: ${env.BRANCH_NAME ?: 'N/A'}
        - Environment: ${env.ENV_NAME}
        - Port: ${env.HTTP_PORT}

        Links:
        - Console: ${env.BUILD_URL}console
        - Artifacts: ${env.BUILD_URL}artifact/

        ${status == 'SUCCESS' ? 'Build successful!' : 'Please check logs for details.'}
    """

    mail(
        to: config.emailRecipients,
        subject: subject,
        body: body
    )
}

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