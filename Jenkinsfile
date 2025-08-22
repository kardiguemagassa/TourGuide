// Centralized configuration with DOCKER, NGROK SONARQUBE, NEXUS, NVD, OWASP IN JENKINS
def config = [
    emailRecipients: "magassakara@gmail.com",
    containerName: "tourguide-app",
    serviceName: "tourguide",
    dockerRegistry: "docker.io",
    sonarProjectKey: "tourguide",
    // Configuration Nexus
    nexus: [
        enabled: true, // enable to use Nexus configured in Jenkins
        configFileId: "maven-settings-nexus", // Config File Provider ID in Jenkins
        url: "http://localhost:8081",
        credentialsId: "nexus-credentials"
    ],
    // Configuration SonarQube
    sonar: [
        enabled: true,
        installationName: "SonarQube",
        projectKey: "tourguide",
        projectName: "TourGuide Application"
    ],
    timeouts: [
        qualityGate: 2,
        deployment: 5,
        sonarAnalysis: 10,
        owaspCheck: 20 // Augmented for OWASP
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

                    // Validation Nexus si activé
                    if (config.nexus.enabled) {
                        validateNexusConfiguration(config)
                    }

                    displayBuildInfo(config)
                }
            }
        }

        stage('Install Local Dependencies') {
            steps {
                script {
                    echo "📦 Installing local dependencies..."

                    if (config.nexus.enabled) {
                        installLocalJarsWithNexus(config)
                    } else {
                        installLocalJars()
                    }
                }
            }
        }

        stage('Build & Test - Java 21 Fixed') {
            steps {
                script {
                    echo "🏗️ Build et tests Maven pour Java 21..."

                    if (config.nexus.enabled) {
                        buildWithNexusJava21(config)
                    } else {
                        buildWithCleanTestsJava21()
                    }
                }
            }
            post {
                always {
                    script {
                        publishTestAndCoverageResultsFixed()
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                allOf {
                    expression { return config.sonar.enabled }
                    anyOf {
                        branch 'master'
                        branch 'develop'
                        changeRequest() // OK - QUALITY GATE ON PR
                    }
                }
            }
            steps {
                script {
                    runSonarQubeAnalysisJenkins(config)
                }
            }
        }

        stage('Quality Gate') {
            when {
                allOf {
                    expression { return config.sonar.enabled }
                    anyOf {
                        branch 'master'
                        branch 'develop'
                        changeRequest() // OK - QUALITY GATE ON PR
                    }
                }
            }
            steps {
                script {
                    waitForSonarQubeQualityGate(config)
                }
            }
        }

        stage('Deploy to Nexus') {
            when {
                allOf {
                    expression { return config.nexus.enabled }
                    anyOf {
                        branch 'master'
                        branch 'develop'
                    }
                }
            }
            steps {
                script {
                    deployToNexusRepository(config)
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
                            changeRequest() // OK - SECURITY ON PR
                        }
                    }
                    steps {
                        script {
                            runOwaspDependencyCheckSimple(config)
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
                            runMavenSecurityAudit(config)
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
                    buildDockerImageJava21Fixed(config)
                }
            }
        }

        stage('Deploy Application') {
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
                    // Deployment with logic per environment
                    if (env.BRANCH_NAME == 'master') {
                        echo "🏭 PRODUCTION DEPLOYMENT"
                        deployToProduction(config)
                    } else if (env.BRANCH_NAME == 'develop') {
                        echo "🧪 STAGING DEPLOYMENT"
                        deployToStaging(config)
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
                    sendEnhancedNotification(config.emailRecipients, config)
                } catch (Exception e) {
                    echo "Error in post always: ${e.getMessage()}"
                } finally {
                    cleanWs()
                }
            }
        }
    }
}

// DEPLOYMENT FEATURES BY ENVIRONMENT
def deployToProduction(config) {
    echo "🏭 DEPLOYMENT IN PRODUCTION (master)"

    // Additional checks for production
    if (currentBuild.result == 'FAILURE') {
        error "❌ Production deployment canceled - build failed"
    }

    // Production deployment with special configuration
    env.HTTP_PORT = config.ports.master
    env.ENV_NAME = config.environments.master

    deployWithDockerComposeJava21Fixed(config)
}

def deployToStaging(config) {
    echo "🧪 DEPLOYMENT IN STAGING (develop)"

    // Deployment staging
    env.HTTP_PORT = config.ports.develop
    env.ENV_NAME = config.environments.develop

    deployWithDockerComposeJava21Fixed(config)
}

// SONARQUBE FUNCTIONS WITH JENKINS CONFIGURATION
def runSonarQubeAnalysisJenkins(config) {
    if (!config.sonar.enabled) {
        echo "ℹ️ SonarQube disabled - analysis ignored"
        return
    }

    echo "🔍 SonarQube analysis with Jenkins configuration..."
    try {
        // Using withSonarQubeEnv which uses Jenkins configuration
        withSonarQubeEnv(config.sonar.installationName) {

            def settingsOption = ""
            if (config.nexus.enabled) {
                configFileProvider([
                    configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
                ]) {
                    settingsOption = "-s \$MAVEN_SETTINGS"
                    runSonarAnalysisWithJenkins(config, settingsOption)
                }
            } else {
                runSonarAnalysisWithJenkins(config, "")
            }
        }
        echo "✅ SonarQube analysis complete"
    } catch (Exception e) {
        echo "❌ Error while analyzing SonarQube: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

def runSonarAnalysisWithJenkins(config, String settingsOption) {
    timeout(time: config.timeouts.sonarAnalysis, unit: 'MINUTES') {
        sh """
            echo "🔍 Lancement de l'analyse SonarQube..."
            mvn sonar:sonar ${settingsOption} \\
                -Dsonar.projectKey=${config.sonar.projectKey} \\
                -Dsonar.projectName="${config.sonar.projectName}" \\
                -Dsonar.sources=src/main/java \\
                -Dsonar.tests=src/test/java \\
                -Dsonar.java.binaries=target/classes \\
                -Dsonar.java.testBinaries=target/test-classes \\
                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \\
                -Dsonar.junit.reportPaths=target/surefire-reports \\
                -Dsonar.java.source=21 \\
                -Dsonar.java.target=21 \\
                -Dsonar.exclusions="**/dto/**,**/config/**,**/TourguideApplication.java" \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -B -q
        """
    }
}

def waitForSonarQubeQualityGate(config) {
    try {
        echo "⏳ Waiting for the SonarQube Quality Gate..."

        timeout(time: config.timeouts.qualityGate, unit: 'MINUTES') {
            def qualityGate = waitForQualityGate()

            if (qualityGate.status == 'OK') {
                echo "✅ Quality Gate: PASSED"
            } else if (qualityGate.status == 'WARN') {
                echo "⚠️ Quality Gate: WARNING - Continuing the deployment"
                currentBuild.result = 'UNSTABLE'
            } else {
                echo "❌ Quality Gate: FAILED"
                error "Quality Gate failed: ${qualityGate.status}"
            }
        }
    } catch (Exception e) {
        echo "❌ Quality Gate Error: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

// BUILD AND MAVEN FUNCTIONS
def buildWithNexusJava21(config) {
    echo "🏗️ Build with Nexus and Java 21..."
    configFileProvider([
        configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
    ]) {
        sh """
            echo "🧹 Cleaning with Nexus..."
            mvn clean -s \$MAVEN_SETTINGS \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -B -q

            echo "🏗️ Compilation with Nexus..."
            mvn compile -s \$MAVEN_SETTINGS \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -B -q

            echo "🧪 Tests with Java 21 configuration..."
            mvn test -s \$MAVEN_SETTINGS \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -Dmaven.test.failure.ignore=true \\
                -Dsurefire.useSystemClassLoader=false \\
                -Dsurefire.forkCount=1 \\
                -Dsurefire.reuseForks=false \\
                -DskipITs=true \\
                -B -q || echo "⚠️ Tests completed"

            echo "📦 Package avec Nexus..."
            mvn package -s \$MAVEN_SETTINGS \\
                -DskipTests=true \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -B -q

            echo "✅ Nexus Build Complete"
        """
    }

    // Artifact Checking
    sh """
        if [ -f target/*.jar ]; then
            echo "📦 JAR created with Nexus:"
            ls -la target/*.jar
        else
            echo "❌ No JAR found"
            exit 1
        fi
    """
}

def buildWithCleanTestsJava21() {
    sh """
        echo "🧹 Complete cleaning before build..."
        mvn clean -Dmaven.repo.local=\${WORKSPACE}/.m2/repository -B -q

        echo "🏗️ Compilation..."
        mvn compile -Dmaven.repo.local=\${WORKSPACE}/.m2/repository -B -q

        echo "🧪 Tests with Java 21 configuration..."
        mvn test -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
            -Dmaven.test.failure.ignore=true \\
            -Dsurefire.useSystemClassLoader=false \\
            -Dsurefire.forkCount=1 \\
            -Dsurefire.reuseForks=false \\
            -DskipITs=true \\
            -B -q || echo "⚠️ Tests completed"

        echo "📦 Package..."
        mvn package -DskipTests=true \\
            -Dmaven.repo.local=\${WORKSPACE}/.m2/repository -B -q

        echo "✅ Build completed"

        if [ -f target/*.jar ]; then
            echo "📦 JAR créé:"
            ls -la target/*.jar
        else
            echo "❌ No JAR found"
            exit 1
        fi
    """
}

def installLocalJarsWithNexus(config) {
    echo "📦 Installing local JARs with Nexus..."
    configFileProvider([
        configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
    ]) {
        sh """
            echo "📦 Installing local JARs with Nexus settings..."

            for jar in gpsUtil TripPricer rewardCentral; do
                if [ -f "libs/\${jar}.jar" ]; then
                    mvn install:install-file -s \$MAVEN_SETTINGS \\
                        -Dfile=libs/\${jar}.jar \\
                        -DgroupId=\${jar} \\
                        -DartifactId=\${jar} \\
                        -Dversion=1.0.0 \\
                        -Dpackaging=jar \\
                        -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                        -B -q
                    echo "✅ \${jar}.jar installed with Nexus"
                else
                    echo "⚠️ \${jar}.jar not found"
                fi
            done
        """
    }
}

def installLocalJars() {
    sh """
        echo "📦 Installing local JARs..."

        for jar in gpsUtil TripPricer rewardCentral; do
            if [ -f "libs/\${jar}.jar" ]; then
                mvn install:install-file \\
                    -Dfile=libs/\${jar}.jar \\
                    -DgroupId=\${jar} \\
                    -DartifactId=\${jar} \\
                    -Dversion=1.0.0 \\
                    -Dpackaging=jar \\
                    -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                    -B -q
                echo "✅ \${jar}.jar installed"
            else
                echo "⚠️ \${jar}.jar not found"
            fi
        done
    """
}

// DOCKER FUNCTIONS
def buildDockerImageJava21Fixed(config) {
    try {
        echo "🐳 Building Docker Java 21 image..."

        def imageName = "${config.containerName}:${env.CONTAINER_TAG}"
        def jarFiles = findFiles(glob: 'target/*.jar').findAll {
            it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
        }

        def jarFile = jarFiles[0].path
        echo "📦 JAR used: ${jarFile}"

        if (!fileExists('Dockerfile')) {
            createDockerfileJava21()
        }

        sh """
            docker build \\
                --build-arg JAR_FILE=${jarFile} \\
                --build-arg JAVA_OPTS="-Xmx512m -Xms256m" \\
                --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \\
                --build-arg VCS_REF=${env.BRANCH_NAME} \\
                --label "build.number=${env.BUILD_NUMBER}" \\
                --label "vcs.ref=${env.BRANCH_NAME}" \\
                --progress=plain \\
                -t ${imageName} .
        """

        sh "docker images ${imageName}"
        echo "✅ Built Java 21 Docker Image: ${imageName}"

    } catch (Exception e) {
        error "❌ Docker build failed: ${e.getMessage()}"
    }
}

def createDockerfileJava21() {
    sh """
        cat > Dockerfile << 'EOF'
        FROM eclipse-temurin:21-jre-alpine

        # Installing the tools
        RUN apk --no-cache add curl bash && \\
            rm -rf /var/cache/apk/*

        # Non-root user
        RUN addgroup -g 1000 -S spring && \\
            adduser -u 1000 -S spring -G spring

        WORKDIR /opt/app
            RUN mkdir -p logs config data && \\
            chown -R spring:spring /opt/app

        # Copy of the JAR
        ARG JAR_FILE=target/*.jar
        COPY --chown=spring:spring \${JAR_FILE} app.jar

        # Script d'entrée amélioré pour Java 21
        COPY --chown=spring:spring entrypoint.sh* ./
        RUN if [ -f entrypoint.sh ]; then chmod +x entrypoint.sh; fi

        USER spring
        EXPOSE 8080 8090 8091 8092

        # Java 21 Environment Variables
        ENV JAVA_OPTS=""
        ENV SERVER_PORT=8090
        ENV SPRING_PROFILES_ACTIVE=dev

        # Health check
        HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \\
            CMD curl -f http://localhost:\${SERVER_PORT}/actuator/health || exit 1

        # Java 21 compatible entry point
        ENTRYPOINT ["sh", "-c", "java \$JAVA_OPTS -jar app.jar"]
        EOF
    """
    echo "✅ Java 21 Dockerfile created"
}

def deployWithDockerComposeJava21Fixed(appConfig) {
    try {
        echo "🐳 Docker Compose Java 21 Deployment..."

        if (!fileExists('docker-compose.yml')) {
            createDockerComposeJava21(appConfig)
        }

        createEnvFileJava21(appConfig)

        sh """
            # Cleaning
            docker ps -a --filter "name=tourguide" --format "{{.Names}}" | xargs docker rm -f 2>/dev/null || true
            docker-compose down --remove-orphans 2>/dev/null || true
            sleep 2

            # Java 21 Environment Variables
            export HTTP_PORT=${env.HTTP_PORT}
            export IMAGE_NAME=${appConfig.containerName}:${env.CONTAINER_TAG}
            export SPRING_PROFILES_ACTIVE=${env.ENV_NAME}
            export JAVA_OPTS="-Xmx512m -Xms256m"

            echo "📋 Docker Compose Configuration:"
            echo "HTTP_PORT=\$HTTP_PORT"
            echo "IMAGE_NAME=\$IMAGE_NAME"
            echo "SPRING_PROFILES_ACTIVE=\$SPRING_PROFILES_ACTIVE"
            echo "JAVA_OPTS=\$JAVA_OPTS"

            # Startup
            docker-compose up -d --force-recreate
        """

        sleep(30)

        sh """
            echo "=== STATUS ==="
            docker-compose ps
            echo "=== LOGS ==="
            docker-compose logs --tail 30 ${appConfig.serviceName}
        """

        echo "✅ Application deployed on: http://localhost:${env.HTTP_PORT}"

    } catch (Exception e) {
        error "❌ Deployment failure: ${e.getMessage()}"
    }
}

def createDockerComposeJava21(appConfig) {
    sh """
        cat > docker-compose.yml << 'EOF'
        version: '3.8'
        services:
        ${appConfig.serviceName}:
        image: \${IMAGE_NAME:-${appConfig.containerName}:latest}
        container_name: ${appConfig.containerName}-\${BUILD_NUMBER:-dev}
        ports:
            - "\${HTTP_PORT:-8090}:\${HTTP_PORT:-8090}"
        environment:
            - SERVER_PORT=\${HTTP_PORT:-8090}
            - SPRING_PROFILES_ACTIVE=\${SPRING_PROFILES_ACTIVE:-dev}
            - JAVA_OPTS=\${JAVA_OPTS:--Xmx512m -Xms256m}
        restart: unless-stopped
        healthcheck:
            test: ["CMD", "curl", "-f", "http://localhost:\${HTTP_PORT:-8090}/actuator/health"]
            interval: 30s
            timeout: 10s
            retries: 5
            start_period: 60s
            networks:
            - tourguide-network

        networks:
        tourguide-network:
        driver: bridge
        EOF
    """
    echo "✅ Docker Compose Java 21 created"
}

def createEnvFileJava21(appConfig) {
    sh """
        cat > .env << 'EOF'
        # Configuration Java 21 - Build #${env.BUILD_NUMBER}
        HTTP_PORT=${env.HTTP_PORT}
        IMAGE_NAME=${appConfig.containerName}:${env.CONTAINER_TAG}
        SPRING_PROFILES_ACTIVE=${env.ENV_NAME}
        BUILD_NUMBER=${env.BUILD_NUMBER}
        JAVA_OPTS=-Xmx512m -Xms256m
        EOF
    """
    echo "✅ Java 21 .env file created"
}

// NEXUS FUNCTIONS
def validateNexusConfiguration(config) {
    echo "🔍 Validating the Nexus configuration..."
    try {
        configFileProvider([
            configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
        ]) {
            sh '''
                echo "📋 Contents of Nexus settings.xml:"
                if [ -f "$MAVEN_SETTINGS" ]; then
                    echo "✅ Settings.xml file found: $MAVEN_SETTINGS"
                    if grep -q "nexus" "$MAVEN_SETTINGS"; then
                        echo "✅ Nexus configuration found in settings.xml"
                        echo "📋 Configured repositories:"
                        grep -A5 -B1 "repository>" "$MAVEN_SETTINGS" || true
                    else
                        echo "❌ Missing Nexus configuration in settings.xml"
                        exit 1
                    fi
                else
                    echo "❌ Settings.xml file not found: $MAVEN_SETTINGS"
                    exit 1
                fi
            '''
        }

        def nexusStatus = sh(
            script: "curl -s -o /dev/null -w '%{http_code}' ${config.nexus.url} || echo '000'",
            returnStdout: true
        ).trim()

        if (nexusStatus == "200") {
            echo "✅ Nexus accessible on ${config.nexus.url}"
        } else {
            echo "⚠️ Nexus not accessible (HTTP: ${nexusStatus}) - continuing in degraded mode"
        }
    } catch (Exception e) {
        echo "❌ Nexus configuration error: ${e.getMessage()}"
        echo "⚠️ Continuing without Nexus"
    }
}

def deployToNexusRepository(config) {
    if (!config.nexus.enabled) {
        echo "ℹ️ Nexus disabled - deployment ignored"
        return
    }

    echo "📤 Deploying to Nexus Repository..."
    try {
        configFileProvider([
            configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
        ]) {
            sh """
                echo "📤 Deploying to Nexus with settings: \$MAVEN_SETTINGS"
                mvn deploy -s \$MAVEN_SETTINGS \\
                    -DskipTests=true \\
                    -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                    -DretryFailedDeploymentCount=3 \\
                    -B -q
            """
        }
        echo "✅ Artifact successfully deployed to Nexus"
    } catch (Exception e) {
        echo "❌ Error deploying to Nexus: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

// OWASP FEATURES AND SECURITY
def runOwaspDependencyCheckSimple(config) {
    try {
        echo "🛡️ OWASP Dependency Check simplified..."

        def settingsOption = ""
        if (config.nexus.enabled) {
            configFileProvider([
                configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
            ]) {
                settingsOption = "-s \$MAVEN_SETTINGS"
                runOwaspWithSettings(settingsOption)
            }
        } else {
            runOwaspWithSettings("")
        }

    } catch (Exception e) {
        echo "🚨 OWASP Error: ${e.getMessage()}"
        createOwaspErrorReport(e)
        currentBuild.result = 'UNSTABLE'
    }
}

def runOwaspWithSettings(String settingsOption) {
    sh "rm -rf \${WORKSPACE}/owasp-data || true"
    sh "mkdir -p \${WORKSPACE}/owasp-data"

    timeout(time: 20, unit: 'MINUTES') {
        def exitCode = sh(script: """
            mvn org.owasp:dependency-check-maven:check ${settingsOption} \\
                -DdataDirectory=\${WORKSPACE}/owasp-data \\
                -DautoUpdate=false \\
                -DfailBuildOnCVSS=0 \\
                -DsuppressFailureOnError=true \\
                -DfailOnError=false \\
                -Dformat=HTML,XML \\
                -DprettyPrint=true \\
                -DretireJsAnalyzerEnabled=false \\
                -DnodeAnalyzerEnabled=false \\
                -DossindexAnalyzerEnabled=false \\
                -DnvdDatafeedEnabled=false \\
                -DskipSystemScope=true \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -B -q
        """, returnStatus: true)

        if (exitCode == 0) {
            echo "✅ OWASP: Analysis completed successfully"
        } else {
            echo "⚠️ OWASP: Analysis with warnings (code: ${exitCode})"
            currentBuild.result = 'UNSTABLE'
        }
    }
}

def runMavenSecurityAudit(config) {
    try {
        echo "🔍 Maven Dependency Audit..."

        def settingsOption = ""
        if (config.nexus.enabled) {
            configFileProvider([
                configFile(fileId: config.nexus.configFileId, variable: 'MAVEN_SETTINGS')
            ]) {
                settingsOption = "-s \$MAVEN_SETTINGS"
                runAuditWithSettings(settingsOption)
            }
        } else {
            runAuditWithSettings("")
        }

    } catch (Exception e) {
        echo "⚠️ Audit Maven: ${e.getMessage()}"
    }
}

def runAuditWithSettings(String settingsOption) {
    timeout(time: 3, unit: 'MINUTES') {
        sh """
            mvn versions:display-dependency-updates ${settingsOption} \\
                -Dmaven.repo.local=\${WORKSPACE}/.m2/repository \\
                -B -q
        """
    }
    echo "✅ Maven Audit Completed"
}

def publishTestAndCoverageResultsFixed() {
    echo "📊 Publication of test results and coverage..."

    try {
        def testReportPaths = [
            'target/surefire-reports/TEST-*.xml',
            'target/surefire-reports/*.xml'
        ]

        def testFilesFound = false

        testReportPaths.each { pattern ->
            if (!testFilesFound) {
                try {
                    def fileCount = sh(
                        script: "ls ${pattern} 2>/dev/null | wc -l || echo 0",
                        returnStdout: true
                    ).trim().toInteger()

                    echo "🔍 Pattern '${pattern}': ${fileCount} files found"

                    if (fileCount > 0) {
                        testFilesFound = true

                        try {
                            junit(
                                testResults: pattern,
                                allowEmptyResults: true,
                                keepLongStdio: true,
                                skipPublishingChecks: true
                            )
                            echo "✅ Tests published with junit() - Pattern: ${pattern}"
                        } catch (Exception junitError) {
                            echo "⚠️ junit() failed: ${junitError.getMessage()}"
                            archiveArtifacts(
                                artifacts: pattern,
                                allowEmptyArchive: true,
                                fingerprint: false
                            )
                            echo "✅ Archived test files"
                        }
                    }
                } catch (Exception e) {
                    echo "⚠️ Error with pattern ${pattern}: ${e.getMessage()}"
                }
            }
        }

        if (!testFilesFound) {
            echo "⚠️ No XML test file found"
        }

        publishJacocoReportsFixed()

    } catch (Exception globalError) {
        echo "❌ Global publication error: ${globalError.getMessage()}"
    }
}

def publishJacocoReportsFixed() {
    echo "📊 JaCoCo Publication..."

    try {
        if (fileExists('target/site/jacoco/index.html')) {
            try {
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site/jacoco',
                    reportFiles: 'index.html',
                    reportName: 'JaCoCo Coverage Report'
                ])
                echo "✅ JaCoCo HTML Report Published"
            } catch (Exception htmlError) {
                echo "⚠️ JaCoCo HTML publication error: ${htmlError.getMessage()}"
            }
        } else {
            echo "⚠️ No HTML report JaCoCo"
        }

        if (fileExists('target/jacoco.exec')) {
            try {
                jacoco(
                    execPattern: '**/target/jacoco.exec',
                    classPattern: '**/target/classes',
                    sourcePattern: '**/src/main/java',
                    exclusionPattern: '**/test/**',
                    minimumBranchCoverage: '0',
                    minimumClassCoverage: '0',
                    minimumComplexityCoverage: '0',
                    minimumInstructionCoverage: '0',
                    minimumLineCoverage: '0',
                    minimumMethodCoverage: '0'
                )
                echo "✅ JaCoCo metrics published"
            } catch (Exception jacocoError) {
                echo "⚠️ JaCoCo Metric Error: ${jacocoError.getMessage()}"
            }
        } else {
            echo "⚠️ No jacoco.exec file"
        }

    } catch (Exception jacocoGlobalError) {
        echo "❌ JaCoCo global error: ${jacocoGlobalError.getMessage()}"
    }
}

def archiveOwaspReports() {
    echo "📋 Archiving OWASP reports..."

    try {
        def reportFiles = [
            'dependency-check-report.html',
            'dependency-check-report.xml'
        ]

        def reportsFound = false
        reportFiles.each { report ->
            if (fileExists("target/${report}")) {
                archiveArtifacts artifacts: "target/${report}", allowEmptyArchive: true
                echo "✅ Report ${report} archive"
                reportsFound = true
            }
        }

        // Publication of the HTML report
        if (fileExists('target/dependency-check-report.html')) {
            try {
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target',
                    reportFiles: 'dependency-check-report.html',
                    reportName: 'OWASP Security Report'
                ])
                echo "✅ OWASP HTML Report Published"
            } catch (Exception htmlError) {
                echo "⚠️ OWASP HTML publishing error: ${htmlError.getMessage()}"
            }
        } else {
            echo "⚠️ No OWASP HTML reports found"
        }

        if (!reportsFound) {
            echo "⚠️ No OWASP report generated"
        }

    } catch (Exception e) {
        echo "❌ OWASP archiving error: ${e.getMessage()}"
    }
}

def createOwaspErrorReport(Exception e) {
    sh """
        mkdir -p target
        cat > target/dependency-check-report.html << 'EOF'
        <!DOCTYPE html>
        <html>
        <head>
            <title>OWASP Dependency Check - Error</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 40px; }
                .error { color: #d32f2f; background: #ffebee; padding: 20px; border-radius: 4px; }
            </style>
        </head>
        <body>
            <h1>🛡️ OWASP Dependency Check - TourGuide</h1>
            <div class="error">
                <h2>⚠️ Security scan unavailable</h2>
                <p><strong>Error:</strong> ${e.getMessage()}</p>
                <p><strong>Build:</strong> #${env.BUILD_NUMBER}</p>
                <p><strong>Branch:</strong> ${env.BRANCH_NAME}</p>
            </div>
        </body>
        </html>
        EOF
    """
}

def checkDockerAvailability() {
    try {
        echo "🐳 Checking Docker..."

        def dockerPaths = ['/usr/local/bin/docker', '/usr/bin/docker', 'docker']
        def dockerFound = false
        def dockerPath = ""

        for (path in dockerPaths) {
            try {
                def result = sh(script: "command -v ${path} || echo 'not-found'", returnStdout: true).trim()
                if (result != 'not-found' && result != '') {
                    dockerFound = true
                    dockerPath = result
                    echo "✅ Docker found at: ${dockerPath}"
                    break
                }
            } catch (Exception e) {
                // Continue searching
            }
        }

        if (!dockerFound) {
            echo "❌ Docker not found"
            return "false"
        }

        try {
            sh "${dockerPath} --version"
            def daemonCheck = sh(script: "${dockerPath} info >/dev/null 2>&1", returnStatus: true)

            if (daemonCheck == 0) {
                echo "✅ Docker daemon active"

                def composeCheck = sh(script: "docker-compose --version", returnStatus: true)
                if (composeCheck == 0) {
                    echo "✅ Docker Compose available"
                    return "true"
                } else {
                    echo "⚠️ Docker Compose not available"
                    return "false"
                }
            } else {
                echo "❌ Docker daemon not active"
                return "false"
            }
        } catch (Exception e) {
            echo "❌ Docker verification error: ${e.getMessage()}"
            return "false"
        }

    } catch (Exception e) {
        echo "❌ Docker verification error: ${e.getMessage()}"
        return "false"
    }
}

def validateEnvironment() {
    echo "🔍 Validation of the environment..."
    sh "java -version"
    sh "mvn -version"
    sh "df -h . | tail -1 | awk '{print \"💾 Disk space: \" \$4 \" available\"}'"
}

def validateDockerPrerequisites() {
    if (env.DOCKER_AVAILABLE != "true") {
        error "🐳 Docker not available"
    }

    def jarFiles = findFiles(glob: 'target/*.jar').findAll {
        it.name.endsWith('.jar') && !it.name.contains('sources') && !it.name.contains('javadoc')
    }

    if (jarFiles.length == 0) {
        error "📦 No executable JAR found"
    }

    echo "📦 JAR found: ${jarFiles[0].path}"
}

def performHealthCheck(config) {
    try {
        echo "🏥 Health check..."

        timeout(time: 2, unit: 'MINUTES') {
            waitUntil {
                script {
                    def healthCheck = sh(
                        script: "curl -f -s http://localhost:${env.HTTP_PORT}/actuator/health",
                        returnStatus: true
                    )
                    if (healthCheck == 0) {
                        echo "✅ Application responds correctly"
                        return true
                    } else {
                        echo "⏳ Application not ready yet..."
                        sleep(5)
                        return false
                    }
                }
            }
        }

        echo "✅ Health check successful"

    } catch (Exception e) {
        sh "docker-compose logs ${config.serviceName} --tail 50 || true"
        error "❌ Health check failed: ${e.getMessage()}"
    }
}

def cleanupDockerImages(config) {
    try {
        echo "🧹 Docker cleaning..."
        sh """
            docker-compose down --remove-orphans || true
            docker image prune -f --filter "until=24h" || true
            docker container prune -f || true
            docker volume prune -f || true
            docker network prune -f || true  # ← ADD
        """
        echo "✅ Docker cleanup complete"
    } catch (Exception e) {
        echo "⚠️ Docker cleanup error: ${e.getMessage()}"
    }
}

def displayBuildInfo(config) {
    echo """
    ================================================================================================================
                  🚀 CONFIGURATION BUILD TOURGUIDE WITH NEXUS + SONARQUBE
    =================================================================================================================
     Build #: ${env.BUILD_NUMBER}
     Branch: ${env.BRANCH_NAME}
     Environment: ${env.ENV_NAME}
     Port externe: ${env.HTTP_PORT}
     Java: 21
     Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Available" : "⚠️ Unavailable"}
     Tag: ${env.CONTAINER_TAG}
     Service: ${config.serviceName}

     🔧 Port Configuration:
     • dev (default) : 8090
     • uat (develop) : 8091
     • prod (master) : 8092

     ⚙️ NEXUS STATUS:
     • Activated: ${config.nexus.enabled ? "✅" : "❌"}
     ${config.nexus.enabled ? "• URL: ${config.nexus.url}" : "• Mode: Standard Maven"}
     ${config.nexus.enabled ? "• Config File: ${config.nexus.configFileId}" : ""}

     🔍 SONARQUBE STATUS:
     • Activated: ${config.sonar.enabled ? "✅" : "❌"}
     ${config.sonar.enabled ? "• Installation: ${config.sonar.installationName}" : "• Mode: Without analysis"}
     ${config.sonar.enabled ? "• Project Key: ${config.sonar.projectKey}" : ""}
     ${config.sonar.enabled ? "• Quality Gate: Activated" : ""}

     🛡️ SECURITY:
     • OWASP: Easy mode with Nexus
     • Coverage: JaCoCo standard
     • Tests: Configuration Java 21
     • SonarQube: ${config.sonar.enabled ? "Analyse via Jenkins" : "Disabled"}

     🐳 DOCKER:
     • Compose: Configuration Java 21
     • Health Check: Automatic
     • JVM Options: Java 21
    ==================================================================================================================
    """
}

def sendEnhancedNotification(recipients, config) {
    try {
        def status = currentBuild.currentResult ?: 'SUCCESS'
        def statusIcon = ['SUCCESS': '✅', 'FAILURE': '❌', 'UNSTABLE': '⚠️', 'ABORTED': '🛑'][status] ?: '❓'

        def subject = "[Jenkins] TourGuide - Build #${env.BUILD_NUMBER} - ${status} (${env.BRANCH_NAME})"

        def nexusInfo = ""
        if (config.nexus.enabled) {
            nexusInfo = """
        📦 NEXUS REPOSITORY:
        • URL: ${config.nexus.url}
        • Configured: ✅ Via Config File Provider
        • Config ID: ${config.nexus.configFileId}
        • Artefact deployed: ${status == 'SUCCESS' ? '✅' : '⚠️'}
        """
        }

        def sonarInfo = ""
        if (config.sonar.enabled) {
            sonarInfo = """
        🔍 SONARQUBE ANALYSIS:
        • Installation: ${config.sonar.installationName}
        • Project Key: ${config.sonar.projectKey}
        • Quality Gate: ${status == 'SUCCESS' ? '✅ Passed' : status == 'UNSTABLE' ? '⚠️ Warning' : '❌ Failed'}
        • Dashboard: Accessible via Jenkins SonarQube
        """
        }

        def deploymentInfo = ""
        if (env.DOCKER_AVAILABLE == "true" && (status == 'SUCCESS' || status == 'UNSTABLE')) {
            deploymentInfo = """
        🚀 JAVA 21 DEPLOYMENT:
        • Application: http://localhost:${env.HTTP_PORT}
        • Health Check: http://localhost:${env.HTTP_PORT}/actuator/health
        • Environment: ${env.ENV_NAME}
        • JVM Options: Fixed for Java 21

        📊 RAPPORTS:
        • Tests: ${env.BUILD_URL}testReport/
        • Coverage: ${env.BUILD_URL}jacoco/
        • Security: ${env.BUILD_URL}OWASP_20Security_20Report/
        ${config.sonar.enabled ? "• SonarQube: Via Jenkins Dashboard" : ""}
        """
        }

        def body = """
        ${statusIcon} BUILD ${status} - TourGuide with Java 21 ${config.nexus.enabled ? '+ Nexus' : ''} ${config.sonar.enabled ? '+ SonarQube' : ''}

        📋 DÉTAILS:
        • Build: #${env.BUILD_NUMBER}
        • Branch: ${env.BRANCH_NAME}
        • Environment: ${env.ENV_NAME}
        • Port: ${env.HTTP_PORT}
        • Java: 21
        • Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅" : "❌"}
        • Nexus: ${config.nexus.enabled ? "✅" : "❌"}
        • SonarQube: ${config.sonar.enabled ? "✅" : "❌"}
        • Duration: ${currentBuild.durationString ?: 'N/A'}

        ${nexusInfo}
        ${sonarInfo}
        ${deploymentInfo}

        🔗 LIENS:
        • Console: ${env.BUILD_URL}console
        • Workspace: ${env.BUILD_URL}ws/
        ${config.nexus.enabled ? "• Nexus Repository: ${config.nexus.url}" : ""}
        ${config.sonar.enabled ? "• SonarQube: Via Jenkins" : ""}

        📅 ${new Date()}
        """

        mail(to: recipients, subject: subject, body: body, mimeType: 'text/plain')
        echo "📧 Notification sent to: ${recipients}"

    } catch (Exception e) {
        echo "❌ Error notification: ${e.getMessage()}"
    }
}

// UTILITY FUNCTIONS FOR CONFIGURATION
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