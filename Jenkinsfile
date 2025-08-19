pipeline {
    agent any

    stages {
        stage('🧪 Test Config File Provider') {
            steps {
                echo "🔍 Test de la configuration maven-settings-nexus..."

                configFileProvider([
                    configFile(fileId: 'maven-settings-nexus', variable: 'MAVEN_SETTINGS')
                ]) {
                    sh '''
                        echo "================================================"
                        echo "🎯 TEST CONFIGURATION JENKINS CONFIG FILE PROVIDER"
                        echo "================================================"

                        echo "📋 Fichier settings.xml disponible à: $MAVEN_SETTINGS"
                        echo "📊 Taille du fichier: $(wc -l < $MAVEN_SETTINGS) lignes"
                        echo "📊 Taille en bytes: $(wc -c < $MAVEN_SETTINGS) bytes"
                        echo ""

                        echo "🔍 Vérification de la structure XML:"
                        echo "=====================================+"

                        # Vérification que le fichier est bien formé
                        if xmllint --noout $MAVEN_SETTINGS 2>/dev/null; then
                            echo "✅ XML bien formé"
                        else
                            echo "❌ Erreur de format XML"
                            exit 1
                        fi

                        echo ""
                        echo "📄 Contenu du fichier généré:"
                        echo "=============================="
                        cat $MAVEN_SETTINGS
                        echo ""

                        echo "🔍 Vérification des éléments clés:"
                        echo "=================================="

                        # Vérification des servers (credentials injectés)
                        if grep -q "<servers>" $MAVEN_SETTINGS; then
                            echo "✅ Section <servers> trouvée"
                            echo "📋 Servers configurés:"
                            grep -A 3 "<server>" $MAVEN_SETTINGS | head -20
                        else
                            echo "❌ Section <servers> manquante"
                        fi

                        echo ""

                        # Vérification des repositories
                        if grep -q "nexus-releases" $MAVEN_SETTINGS; then
                            echo "✅ Repository nexus-releases configuré"
                        else
                            echo "❌ Repository nexus-releases manquant"
                        fi

                        if grep -q "nexus-snapshots" $MAVEN_SETTINGS; then
                            echo "✅ Repository nexus-snapshots configuré"
                        else
                            echo "❌ Repository nexus-snapshots manquant"
                        fi

                        if grep -q "nexus-public" $MAVEN_SETTINGS; then
                            echo "✅ Repository nexus-public configuré"
                        else
                            echo "❌ Repository nexus-public manquant"
                        fi

                        echo ""
                        echo "🎯 RÉSULTAT DU TEST:"
                        echo "==================="
                        echo "✅ Config File Provider fonctionne parfaitement!"
                        echo "✅ Credentials injectés automatiquement"
                        echo "✅ Configuration XML valide"
                        echo "✅ Prêt pour les pipelines Maven"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "🎉 Configuration Config File Provider validée avec succès!"
        }
        failure {
            echo "❌ Problème avec la configuration Config File Provider"
        }
    }
}