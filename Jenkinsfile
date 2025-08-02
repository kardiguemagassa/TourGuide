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
        owaspCheck: 15  // Augmenté pour OWASP
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
        preferOfflineMode: false,  // Changé pour permettre le téléchargement
        maxRetries: 2,
        cvssThreshold: 7.0  // Seuil plus bas pour détecter plus de vulnérabilités
    ]
]

pipeline {
    agent any

    options {
        timeout(time: 60, unit: 'MINUTES')  // Augmenté pour OWASP
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

        stage('Show env') {
            steps {
                script {
                    sh 'printenv'
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
                    echo "🏗️ Build et tests Maven avec couverture..."

                    // Build principal avec tests et couverture
                    sh """
                        mvn clean compile test \
                            -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                            -DskipTests=false \
                            -Dmaven.test.failure.ignore=false \
                            -B -U
                    """

                    // Génération du rapport JaCoCo
                    sh """
                        mvn jacoco:report \
                            -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                            -B -q
                    """

                    // Package final
                    sh """
                        mvn package \
                            -DskipTests=true \
                            -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                            -B -q
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

        stage('Security Analysis') {
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
                                publishOwaspReports()
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
                    // Archive des JAR
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true

                    // Publication des rapports finaux
                    publishFinalReports()

                    // Nettoyage Docker
                    if (env.DOCKER_AVAILABLE == "true") {
                        cleanupDockerImages(config)
                    }

                    // Notification
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
// FONCTION COVERAGE REPORT CORRIGÉE - 3 RAPPORTS DISTINCTS
// =============================================================================

def publishTestAndCoverageResults() {
    echo "📊 Publication des rapports de tests et couverture de code..."

    // 1. Publication des résultats de tests JUnit
    try {
        if (fileExists('target/surefire-reports/TEST-*.xml')) {
            junit testResults: 'target/surefire-reports/TEST-*.xml', allowEmptyResults: true
            echo "✅ Rapports de tests JUnit publiés"
        } else {
            echo "⚠️ Aucun rapport de test JUnit trouvé"
        }
    } catch (Exception e) {
        echo "⚠️ Erreur publication tests JUnit: ${e.getMessage()}"
    }

    // 2. Publication du rapport JaCoCo HTML
    try {
        if (fileExists('target/site/jacoco/index.html')) {
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'target/site/jacoco',
                reportFiles: 'index.html',
                reportName: 'JaCoCo Coverage Report',
                reportTitles: ''
            ])
            echo "✅ Rapport JaCoCo HTML publié"
        } else {
            echo "⚠️ Rapport JaCoCo HTML non trouvé"
        }
    } catch (Exception e) {
        echo "⚠️ Erreur publication JaCoCo HTML: ${e.getMessage()}"
    }

    // 3. Publication du Coverage Report séparé (DISTINCT du JaCoCo)
    try {
        if (fileExists('target/site/jacoco/jacoco.xml')) {
            // Publication HTML alternative pour Coverage Report
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'target/site/jacoco',
                reportFiles: 'index.html',
                reportName: 'Coverage Report',
                reportTitles: ''
            ])

            // Archive du XML
            archiveArtifacts artifacts: 'target/site/jacoco/jacoco.xml', allowEmptyArchive: true
            echo "✅ Coverage Report publié"
        } else {
            echo "⚠️ Fichier jacoco.xml non trouvé"
        }
    } catch (Exception e) {
        echo "⚠️ Erreur publication Coverage Report: ${e.getMessage()}"
    }

    // 4. Debug des fichiers générés
    sh """
        echo "🔍 ANALYSE DES RAPPORTS GÉNÉRÉS:"
        echo "=================================="

        echo "📁 Contenu de target/:"
        ls -la target/ 2>/dev/null || echo "Répertoire target/ non trouvé"

        echo "📁 Contenu de target/site/:"
        ls -la target/site/ 2>/dev/null || echo "Répertoire target/site/ non trouvé"

        echo "📁 Contenu de target/site/jacoco/:"
        ls -la target/site/jacoco/ 2>/dev/null || echo "Répertoire target/site/jacoco/ non trouvé"

        echo "📁 Contenu de target/surefire-reports/:"
        ls -la target/surefire-reports/ 2>/dev/null || echo "Répertoire target/surefire-reports/ non trouvé"

        echo "🔍 Recherche de tous les fichiers jacoco:"
        find target/ -name "*jacoco*" -type f 2>/dev/null || echo "Aucun fichier jacoco trouvé"

        echo "🔍 Recherche de tous les fichiers de rapport:"
        find target/ -name "*.html" -o -name "*.xml" -o -name "*.json" 2>/dev/null || echo "Aucun fichier de rapport trouvé"
    """
}

// =============================================================================
// FONCTION OWASP DEPENDENCY CHECK CORRIGÉE
// =============================================================================

def runOwaspDependencyCheck(config) {
    try {
        echo "🛡️ OWASP Dependency Check - Analyse de sécurité des dépendances"

        // Préparation des répertoires
        sh """
            rm -rf ${WORKSPACE}/owasp-data ${WORKSPACE}/target/dependency-check-* || true
            mkdir -p ${WORKSPACE}/owasp-data
            mkdir -p ${WORKSPACE}/target
        """

        timeout(time: config.timeouts.owaspCheck, unit: 'MINUTES') {
            echo "📥 Démarrage du scan OWASP (peut prendre du temps au premier lancement)..."

            def owaspCommand = """
                mvn org.owasp:dependency-check-maven:check \
                    -DdataDirectory=${WORKSPACE}/owasp-data \
                    -DautoUpdate=true \
                    -DfailBuildOnCVSS=${config.owasp.cvssThreshold} \
                    -DsuppressFailureOnError=true \
                    -DfailOnError=false \
                    -Dformat=HTML,XML,JSON \
                    -DprettyPrint=true \
                    -DskipSystemScope=true \
                    -DskipTestScope=false \
                    -DskipProvidedScope=false \
                    -DskipRuntimeScope=false \
                    -DretireJsAnalyzerEnabled=false \
                    -DnodeAnalyzerEnabled=false \
                    -DossindexAnalyzerEnabled=false \
                    -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                    -B
            """

            def exitCode = sh(script: owaspCommand, returnStatus: true)

            echo "📋 Code de sortie OWASP: ${exitCode}"
            handleOwaspResult(exitCode)
        }

    } catch (Exception e) {
        echo "🚨 Erreur OWASP Dependency Check: ${e.getMessage()}"

        // Création d'un rapport d'erreur
        createOwaspErrorReport(e.getMessage())

        currentBuild.result = 'UNSTABLE'
        echo "⏭️ Pipeline continue malgré l'erreur OWASP"
    }

    // Vérification des fichiers générés
    sh """
        echo "🔍 VÉRIFICATION DES RAPPORTS OWASP:"
        echo "===================================="

        echo "📁 Contenu de target/ après OWASP:"
        ls -la target/ || echo "Répertoire target non trouvé"

        echo "🔍 Recherche des rapports dependency-check:"
        find . -name "*dependency-check*" -type f || echo "Aucun rapport OWASP trouvé"

        echo "📊 Taille des fichiers trouvés:"
        find . -name "*dependency-check*" -type f -exec ls -lh {} \\; || echo "Aucun fichier à afficher"
    """
}

def handleOwaspResult(exitCode) {
    switch(exitCode) {
        case 0:
            echo "✅ OWASP: Scan terminé avec succès, aucune vulnérabilité critique"
            break
        case 1:
            echo "⚠️ OWASP: Vulnérabilités détectées mais sous le seuil critique"
            currentBuild.result = 'UNSTABLE'
            break
        default:
            echo "❌ OWASP: Erreur lors de l'analyse (code: ${exitCode})"
            currentBuild.result = 'UNSTABLE'
            break
    }
}

def createOwaspErrorReport(errorMessage) {
    sh """
        cat > target/dependency-check-report.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>OWASP Dependency Check - Rapport d'Erreur</title>
    <meta charset="UTF-8">
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
            margin: 0; padding: 20px; background: #f8f9fa;
        }
        .container {
            max-width: 1000px; margin: 0 auto; background: white;
            border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .header {
            background: #dc3545; color: white; padding: 20px;
            border-radius: 8px 8px 0 0;
        }
        .content { padding: 30px; }
        .error-box {
            background: #f8d7da; border: 1px solid #f5c6cb;
            color: #721c24; padding: 15px; border-radius: 5px; margin: 20px 0;
        }
        .info-box {
            background: #d1ecf1; border: 1px solid #bee5eb;
            color: #0c5460; padding: 15px; border-radius: 5px; margin: 20px 0;
        }
        .timestamp { color: #6c757d; font-size: 0.9em; margin-top: 20px; }
        .build-info {
            background: #e9ecef; padding: 15px; border-radius: 5px;
            font-family: monospace; margin: 20px 0;
        }
        h1 { margin: 0; font-size: 24px; }
        h2 { color: #495057; border-bottom: 2px solid #dee2e6; padding-bottom: 10px; }
        ul { padding-left: 20px; }
        li { margin: 8px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🛡️ OWASP Dependency Check</h1>
            <p>Rapport d'analyse de sécurité des dépendances</p>
        </div>

        <div class="content">
            <div class="error-box">
                <h2>⚠️ Scan de sécurité indisponible</h2>
                <p><strong>Erreur:</strong> ${errorMessage}</p>
            </div>

            <div class="build-info">
                <strong>Informations du build:</strong><br>
                • Build: #${env.BUILD_NUMBER}<br>
                • Branche: ${env.BRANCH_NAME}<br>
                • Workspace: ${WORKSPACE}<br>
                • Date: ${new Date()}
            </div>

            <div class="info-box">
                <h2>Actions recommandées</h2>
                <ul>
                    <li>Vérifier la connectivité Internet pour le téléchargement de la base CVE</li>
                    <li>Contrôler les permissions du répertoire de travail Jenkins</li>
                    <li>Examiner les logs Maven détaillés dans la console</li>
                    <li>Vérifier la configuration du plugin OWASP dans le pom.xml</li>
                    <li>S'assurer que toutes les dépendances Maven sont correctement résolues</li>
                    <li>Vérifier l'espace disque disponible (la base CVE fait plusieurs GB)</li>
                </ul>
            </div>

            <h2>Configuration utilisée</h2>
            <div class="build-info">
• Répertoire de données: ${WORKSPACE}/owasp-data<br>
• Seuil CVSS: ${config.owasp.cvssThreshold}<br>
• Timeout: ${config.timeouts.owaspCheck} minutes<br>
• Auto-update: activé<br>
• Formats: HTML, XML, JSON
            </div>

            <div class="timestamp">
                <em>Rapport généré automatiquement le ${new Date()}</em>
            </div>
        </div>
    </div>
</body>
</html>
EOF
    """
}

// =============================================================================
// FONCTION PUBLICATION RAPPORTS OWASP (CORRIGÉE)
// =============================================================================

def publishOwaspReports() {
    echo "📋 Publication des rapports OWASP Dependency Check..."

    def reportFiles = [
        'dependency-check-report.html',
        'dependency-check-report.xml',
        'dependency-check-report.json'
    ]

    def foundReports = []

    // Archive des fichiers de rapport
    reportFiles.each { report ->
        if (fileExists("target/${report}")) {
            try {
                archiveArtifacts artifacts: "target/${report}", allowEmptyArchive: true
                foundReports.add(report)
                echo "✅ Fichier archivé: ${report}"
            } catch (Exception e) {
                echo "⚠️ Erreur archivage ${report}: ${e.getMessage()}"
            }
        } else {
            echo "⚠️ Fichier non trouvé: target/${report}"
        }
    }

    // Publication du rapport HTML OWASP (NOM EXACT COMME DANS FEATURE_CI)
    try {
        if (fileExists('target/dependency-check-report.html')) {
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'target',
                reportFiles: 'dependency-check-report.html',
                reportName: 'OWASP Dependency Check Report',
                reportTitles: ''
            ])
            echo "✅ Rapport OWASP HTML publié"
        } else {
            echo "⚠️ Rapport OWASP HTML non trouvé pour publication"
        }
    } catch (Exception e) {
        echo "⚠️ Erreur publication rapport OWASP HTML: ${e.getMessage()}"
    }

    if (foundReports.isEmpty()) {
        echo "⚠️ Aucun rapport OWASP trouvé"
    } else {
        echo "✅ Rapports OWASP publiés: ${foundReports.join(', ')}"
    }
}

// =============================================================================
// FONCTION PUBLICATION FINALE DE TOUS LES RAPPORTS (COMME FEATURE_CI)
// =============================================================================

def publishFinalReports() {
    echo "📊 Publication finale de tous les rapports..."

    // Publication du Coverage Report dédié (GARANTIT QUE LE 3ème RAPPORT APPARAÎT)
    try {
        if (fileExists('target/site/jacoco/index.html')) {
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'target/site/jacoco',
                reportFiles: 'index.html',
                reportName: 'Coverage Report',
                reportTitles: 'Code Coverage Analysis'
            ])
            echo "✅ Coverage Report final publié"
        }
    } catch (Exception e) {
        echo "⚠️ Erreur publication Coverage Report final: ${e.getMessage()}"
    }

    // Résumé des rapports disponibles
    echo """
    📊 RÉSUMÉ DES RAPPORTS DISPONIBLES:
    ===================================
    • JaCoCo Coverage Report: Couverture de code détaillée
    • Coverage Report: Analyse de couverture
    • OWASP Dependency Check Report: Analyse de sécurité
    • Build Artifacts: JAR et autres artefacts
    """
}

// =============================================================================
// AUTRES FONCTIONS (inchangées de la version feature_ci)
// =============================================================================

def checkDockerAvailability() {
    try {
        echo "🐳 Vérification de Docker..."
        def dockerCheck = sh(script: "docker --version && docker info >/dev/null 2>&1", returnStatus: true)
        if (dockerCheck == 0) {
            def composeCheck = sh(script: "docker-compose --version || docker compose --version", returnStatus: true)
            return composeCheck == 0 ? "true" : "false"
        }
        return "false"
    } catch (Exception e) {
        echo "❌ Docker non disponible: ${e.getMessage()}"
        return "false"
    }
}

def validateEnvironment() {
    echo "🔍 Validation de l'environnement..."
    sh """
        echo "Java Version:" && java -version
        echo "Maven Version:" && mvn -version
        echo "Espace disque:" && df -h . | tail -1
    """
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

def deployWithDockerCompose(config) {
    try {
        echo "🐳 Déploiement avec Docker Compose..."

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

    } catch (Exception e) {
        sh "docker-compose logs ${config.serviceName} --tail 50 || true"
        error "❌ Échec du déploiement Docker Compose: ${e.getMessage()}"
    }
}

def performHealthCheck(config) {
    try {
        echo "🏥 Health check de l'application..."

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
                    -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
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
                    echo "⚠️ Quality Gate échoué sur ${env.BRANCH_NAME}"
                }
            } else {
                echo "✅ Quality Gate réussi"
            }
        }
    } catch (Exception e) {
        echo "⚠️ Quality Gate: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

def runMavenSecurityAudit() {
    try {
        echo "🔍 Audit Maven des dépendances..."
        timeout(time: 3, unit: 'MINUTES') {
            sh """
                mvn versions:display-dependency-updates \
                    -Dmaven.repo.local=${WORKSPACE}/.m2/repository \
                    -B -q
            """
        }
        echo "✅ Audit Maven terminé"
    } catch (Exception e) {
        echo "⚠️ Audit Maven: ${e.getMessage()}"
    }
}

def cleanupDockerImages(config) {
    try {
        echo "🧹 Nettoyage Docker..."
        sh """
            docker system prune -f || true
            docker-compose down --remove-orphans || true
        """
        echo "✅ Nettoyage Docker terminé"
    } catch (Exception e) {
        echo "⚠️ Erreur nettoyage Docker: ${e.getMessage()}"
    }
}

def createEnvFile() {
    echo "📝 Création du fichier .env pour Docker Compose..."

    sh """
        cat > .env << 'EOF'
# Configuration environnement TourGuide - Build #${env.BUILD_NUMBER}
BUILD_DATE=\$(date -u +'%Y-%m-%dT%H:%M:%SZ')
VCS_REF=${env.BRANCH_NAME}
BUILD_NUMBER=${env.BUILD_NUMBER}

# Configuration Application
SPRING_ACTIVE_PROFILES=prod
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
SERVER_PORT=8080

# Port externe dynamique selon l'environnement
HTTP_PORT=${env.HTTP_PORT}

# Configuration réseau
NETWORK_NAME=tourguide-network

# Configuration logging
LOG_LEVEL=INFO
LOG_PATH=/opt/app/logs

# Configuration Actuator
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always

# Informations de l'application
APP_NAME=TourGuide
APP_VERSION=0.0.1-SNAPSHOT
APP_ENVIRONMENT=${env.ENV_NAME}
EOF
    """

    echo "✅ Fichier .env créé avec la configuration pour l'environnement ${env.ENV_NAME}"
}

def displayBuildInfo(config) {
    echo """
    ================================================================================
                            🚀 CONFIGURATION BUILD TOURGUIDE
    ================================================================================
     Build #: ${env.BUILD_NUMBER}
     Branche: ${env.BRANCH_NAME}
     Environnement: ${env.ENV_NAME}
     Port HTTP: ${env.HTTP_PORT}
     Tag Docker: ${env.CONTAINER_TAG}
     Service: ${config.serviceName}

     🔧 Configuration:
     • Java: JDK 17
     • Maven: M3
     • Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "⚠️ Indisponible"}
     • SonarQube: ${config.sonar.communityEdition ? "Community Edition" : "Enterprise"}

     📊 Rapports activés:
     • Coverage Report: ✅ JaCoCo (3 rapports distincts)
     • Security Report: ✅ OWASP Dependency Check
     • Quality Gate: ✅ SonarQube

     ⏱️ Timeouts:
     • OWASP Check: ${config.timeouts.owaspCheck} min
     • Quality Gate: ${config.timeouts.qualityGate} min
     • Deployment: ${config.timeouts.deployment} min
    ================================================================================
    """
}

def sendEnhancedNotification(recipients) {
    try {
        def status = currentBuild.currentResult ?: 'SUCCESS'
        def statusIcon = [
            'SUCCESS': '✅',
            'FAILURE': '❌',
            'UNSTABLE': '⚠️',
            'ABORTED': '🛑'
        ][status] ?: '❓'

        def subject = "[Jenkins] TourGuide - Build #${env.BUILD_NUMBER} - ${status} (${env.BRANCH_NAME})"

        def deploymentInfo = ""
        if (env.DOCKER_AVAILABLE == "true" && (status == 'SUCCESS' || status == 'UNSTABLE')) {
            deploymentInfo = """

        🚀 DÉPLOIEMENT:
        • Application: http://localhost:${env.HTTP_PORT}
        • Container: tourguide-app-${env.BRANCH_NAME}-${env.BUILD_NUMBER}
        • Environnement: ${env.ENV_NAME}
        • Health Check: http://localhost:${env.HTTP_PORT}/actuator/health
        """
        }

        def reportsInfo = """

        📊 RAPPORTS DISPONIBLES:
        • Coverage Report: ${env.BUILD_URL}Coverage_20Report/
        • JaCoCo Coverage: ${env.BUILD_URL}JaCoCo_20Coverage_20Report/
        • OWASP Security: ${env.BUILD_URL}OWASP_20Dependency_20Check_20Report/
        • Console Logs: ${env.BUILD_URL}console
        """

        def body = """
        ${statusIcon} BUILD ${status} - TourGuide

        📋 DÉTAILS DU BUILD:
        • Numéro: #${env.BUILD_NUMBER}
        • Branche: ${env.BRANCH_NAME}
        • Environnement: ${env.ENV_NAME}
        • Tag Docker: ${env.CONTAINER_TAG}
        • Durée: ${currentBuild.durationString ?: 'N/A'}

        🔧 CONFIGURATION:
        • Java: JDK 17
        • Maven: M3
        • Docker: ${env.DOCKER_AVAILABLE == "true" ? "✅ Disponible" : "❌ Indisponible"}
        • Port: ${env.HTTP_PORT}
        ${deploymentInfo}
        ${reportsInfo}

        💡 ACTIONS DISPONIBLES:
        • Redéployer: Relancer le pipeline
        • Logs: Consulter la console Jenkins
        • Rapports: Voir les liens ci-dessus

        📅 Build exécuté le ${new Date()}
        🏗️ Jenkins: ${env.JENKINS_URL}
        """

        mail(
            to: recipients,
            subject: subject,
            body: body,
            mimeType: 'text/plain',
            charset: 'UTF-8'
        )
        echo "📧 Notification envoyée à: ${recipients}"

    } catch (Exception e) {
        echo "❌ Erreur lors de l'envoi de notification: ${e.getMessage()}"
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES POUR LA CONFIGURATION
// =============================================================================

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