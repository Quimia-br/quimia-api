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

        stage('Publicar imagem da release') {
            when {
                tag pattern: 'v*', comparator: 'GLOB'
            }
            steps {
                script {
                    publishChecks(
                        name: 'Publicar imagem da release',
                        title: 'Publicar imagem da release',
                        status: 'IN_PROGRESS',
                        summary: 'Construindo e publicando a imagem da release no registry.'
                    )

                    try {
                        def imageTag = env.TAG_NAME
                        if (!(imageTag ==~ /^v\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/)) {
                            error("Tag de release inválida: ${imageTag}. Use o formato vMAJOR.MINOR.PATCH.")
                        }

                        def registryHost = env.QUIMIA_REGISTRY_HOST ?: (isUnix() ? '172.17.0.1:5000' : 'localhost:5000')
                        def imageReference = "${registryHost}/quimia-api:${imageTag}"

                        sh "sh mvnw -B -ntp com.google.cloud.tools:jib-maven-plugin:3.4.6:build -Dimage=${imageReference} -Djib.allowInsecureRegistries=true"

                        publishChecks(
                            name: 'Publicar imagem da release',
                            title: 'Publicar imagem da release',
                            status: 'COMPLETED',
                            conclusion: 'SUCCESS',
                            summary: "Imagem ${imageReference} publicada com sucesso."
                        )
                    } catch (err) {
                        publishChecks(
                            name: 'Publicar imagem da release',
                            title: 'Publicar imagem da release',
                            status: 'COMPLETED',
                            conclusion: 'FAILURE',
                            summary: 'Falha ao construir ou publicar a imagem da release.'
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
