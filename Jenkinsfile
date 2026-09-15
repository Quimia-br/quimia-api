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

        stage('Build') {
            steps {
                script {
                    publishChecks(
                        name: 'Build',
                        title: 'Build',
                        status: 'IN_PROGRESS',
                        summary: 'Compilando a aplicação.'
                    )

                    try {
                        sh 'sh mvnw -B -ntp -DskipTests compile'
                        publishChecks(
                            name: 'Build',
                            title: 'Build',
                            status: 'COMPLETED',
                            conclusion: 'SUCCESS',
                            summary: 'Build concluído com sucesso.'
                        )
                    } catch (err) {
                        publishChecks(
                            name: 'Build',
                            title: 'Build',
                            status: 'COMPLETED',
                            conclusion: 'FAILURE',
                            summary: 'Falha durante o build.'
                        )
                        throw err
                    }
                }
            }
        }

        stage('Testes unitários') {
            steps {
                script {
                    publishChecks(
                        name: 'Testes unitários',
                        title: 'Testes unitários',
                        status: 'IN_PROGRESS',
                        summary: 'Executando os testes automatizados.'
                    )

                    try {
                        sh 'sh mvnw -B -ntp test'
                        publishChecks(
                            name: 'Testes unitários',
                            title: 'Testes unitários',
                            status: 'COMPLETED',
                            conclusion: 'SUCCESS',
                            summary: 'Testes automatizados aprovados.'
                        )
                    } catch (err) {
                        publishChecks(
                            name: 'Testes unitários',
                            title: 'Testes unitários',
                            status: 'COMPLETED',
                            conclusion: 'FAILURE',
                            summary: 'Um ou mais testes falharam.'
                        )
                        throw err
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    publishChecks(
                        name: 'Quality Gate',
                        title: 'Quality Gate',
                        status: 'IN_PROGRESS',
                        summary: 'Validando cobertura e empacotando a aplicação.'
                    )

                    try {
                        sh 'sh mvnw -B -ntp verify -DskipTests'
                        publishChecks(
                            name: 'Quality Gate',
                            title: 'Quality Gate',
                            status: 'COMPLETED',
                            conclusion: 'SUCCESS',
                            summary: 'Quality Gate aprovado.'
                        )
                    } catch (err) {
                        publishChecks(
                            name: 'Quality Gate',
                            title: 'Quality Gate',
                            status: 'COMPLETED',
                            conclusion: 'FAILURE',
                            summary: 'Quality Gate reprovado.'
                        )
                        throw err
                    }
                }
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
