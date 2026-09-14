pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(false)
    }

    stages {
        stage('Validar toolchain') {
            steps {
                sh 'java -version'
                sh 'sh mvnw -version'
            }
        }

        stage('Compilar') {
            steps {
                sh 'sh mvnw -B -ntp -DskipTests compile'
            }
        }

        stage('Testes unitários e automatizados') {
            steps {
                sh 'sh mvnw -B -ntp test'
            }
        }

        stage('Quality Gate e empacotamento') {
            steps {
                sh 'sh mvnw -B -ntp verify -DskipTests'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'target/*.jar'
        }
    }
}
