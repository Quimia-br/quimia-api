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

        stage('Promover imagem no GitOps') {
            when {
                tag pattern: 'v*', comparator: 'GLOB'
            }
            steps {
                script {
                    publishChecks(
                        name: 'Promover imagem no GitOps',
                        title: 'Promover imagem no GitOps',
                        status: 'IN_PROGRESS',
                        summary: 'Atualizando o repositório GitOps e preparando auto-merge.'
                    )

                    try {
                        def releaseTag = env.TAG_NAME
                        def gitOpsBranch = "automation/quimia-api-${releaseTag}"

                        withCredentials([
                            string(
                                credentialsId: 'github-quimia-gitops-write',
                                variable: 'GITOPS_TOKEN'
                            )
                        ]) {
                            withEnv([
                                "RELEASE_TAG=${releaseTag}",
                                "GITOPS_BRANCH=${gitOpsBranch}"
                            ]) {
                                sh '''
                                    set +x
                                    set -eu

                                    gitops_dir="$WORKSPACE/gitops-release"
                                    askpass="$WORKSPACE/.gitops-askpass"
                                    pr_body="$WORKSPACE/.gitops-pr.json"
                                    merge_body="$WORKSPACE/.gitops-auto-merge.json"

                                    cleanup() {
                                        rm -f "$askpass" "$pr_body" "$merge_body"
                                    }
                                    trap cleanup EXIT

                                    if [ -e "$gitops_dir" ]; then
                                        echo "Diretório de trabalho do GitOps já existe: $gitops_dir"
                                        exit 1
                                    fi

                                    cat > "$askpass" <<'EOF'
#!/bin/sh
case "$1" in
  *Username*) printf '%s\\n' 'x-access-token' ;;
  *) printf '%s\\n' "$GITOPS_TOKEN" ;;
esac
EOF
                                    chmod 700 "$askpass"
                                    export GIT_ASKPASS="$askpass"
                                    export GIT_TERMINAL_PROMPT=0

                                    git clone --depth 1 https://github.com/Quimia-br/quimia-gitops.git "$gitops_dir"
                                    cd "$gitops_dir"
                                    git config user.name "quimia-jenkins[bot]"
                                    git config user.email "quimia-jenkins[bot]@users.noreply.github.com"
                                    git switch -c "$GITOPS_BRANCH"

                                    overlay='apps/quimia-api/overlays/dev/kustomization.yaml'
                                    sed -i -E "s/^(    newTag: ).*/\\1${RELEASE_TAG}/" "$overlay"
                                    test "$(grep -c '^    newTag: ' "$overlay")" -eq 1

                                    changed_files="$(git diff --name-only)"
                                    test "$changed_files" = "$overlay"
                                    if git diff --quiet -- "$overlay"; then
                                        echo "O GitOps já aponta para ${RELEASE_TAG}; nada a promover."
                                        exit 1
                                    fi

                                    git add -- "$overlay"
                                    git commit -m "ci: promove quimia-api ${RELEASE_TAG}"
                                    git push origin "HEAD:${GITOPS_BRANCH}"

                                    cd "$WORKSPACE"
                                    cat > "$pr_body" <<EOF
{"title":"ci: promove quimia-api ${RELEASE_TAG}","head":"${GITOPS_BRANCH}","base":"main","body":"Imagem publicada: \\`localhost:5000/quimia-api:${RELEASE_TAG}\\`.\\n\\nAlteração gerada pelo Jenkins após os checks da release."}
EOF

                                    pr_response="$(curl --fail --silent --show-error \\
                                        -X POST \\
                                        -H 'Accept: application/vnd.github+json' \\
                                        -H 'X-GitHub-Api-Version: 2022-11-28' \\
                                        -H "Authorization: Bearer ${GITOPS_TOKEN}" \\
                                        https://api.github.com/repos/Quimia-br/quimia-gitops/pulls \\
                                        --data-binary "@${pr_body}")"

                                    pr_node_id="$(printf '%s' "$pr_response" | sed -n 's/.*"node_id":[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1)"
                                    pr_url="$(printf '%s' "$pr_response" | sed -n 's/.*"html_url":[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1)"
                                    test -n "$pr_node_id"
                                    test -n "$pr_url"
                                    printf '%s\\n' "$pr_url" > "$WORKSPACE/gitops-pr-url.txt"

                                    cat > "$merge_body" <<EOF
{"query":"mutation { enablePullRequestAutoMerge(input: {pullRequestId: \\\"${pr_node_id}\\\", mergeMethod: SQUASH, commitHeadline: \\\"ci: promove quimia-api ${RELEASE_TAG}\\\"}) { pullRequest { url } } }"}
EOF

                                    merge_response="$(curl --fail --silent --show-error \\
                                        -X POST \\
                                        -H 'Accept: application/vnd.github+json' \\
                                        -H 'X-GitHub-Api-Version: 2022-11-28' \\
                                        -H "Authorization: Bearer ${GITOPS_TOKEN}" \\
                                        https://api.github.com/graphql \\
                                        --data-binary "@${merge_body}")"

                                    if printf '%s' "$merge_response" | grep -q '"errors"'; then
                                        echo 'GitHub recusou o auto-merge da PR do GitOps.'
                                        exit 1
                                    fi
                                '''
                            }
                        }

                        def pullRequestUrl = readFile('gitops-pr-url.txt').trim()
                        sh 'rm -f gitops-pr-url.txt'
                        publishChecks(
                            name: 'Promover imagem no GitOps',
                            title: 'Promover imagem no GitOps',
                            status: 'COMPLETED',
                            conclusion: 'SUCCESS',
                            summary: "PR ${pullRequestUrl} criada com auto-merge habilitado."
                        )
                    } catch (err) {
                        publishChecks(
                            name: 'Promover imagem no GitOps',
                            title: 'Promover imagem no GitOps',
                            status: 'COMPLETED',
                            conclusion: 'FAILURE',
                            summary: 'Falha ao criar ou habilitar o auto-merge da PR do GitOps.'
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
