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

                        def isEcrRegistry = registryHost ==~ /^[0-9]+\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com$/

                        withEnv([
                            "IMAGE_REFERENCE=${imageReference}",
                            "IS_ECR_REGISTRY=${isEcrRegistry}"
                        ]) {
                            sh '''
                                set +x
                                set -eu

                                if [ "$IS_ECR_REGISTRY" = "true" ]; then
                                    test -n "${AWS_REGION:-}" || {
                                        echo 'AWS_REGION é obrigatório para publicar no ECR.' >&2
                                        exit 1
                                    }

                                    ecr_password="$(aws ecr get-login-password --region "$AWS_REGION")"
                                    test -n "$ecr_password"

                                    sh mvnw -B -ntp \
                                        com.google.cloud.tools:jib-maven-plugin:3.4.6:build \
                                        "-Dimage=$IMAGE_REFERENCE" \
                                        '-Djib.to.auth.username=AWS' \
                                        "-Djib.to.auth.password=$ecr_password" \
                                        '-Djib.allowInsecureRegistries=false'
                                else
                                    sh mvnw -B -ntp \
                                        com.google.cloud.tools:jib-maven-plugin:3.4.6:build \
                                        "-Dimage=$IMAGE_REFERENCE" \
                                        '-Djib.allowInsecureRegistries=true'
                                fi
                            '''
                        }

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
                        def registryHost = env.QUIMIA_REGISTRY_HOST ?: (isUnix() ? '172.17.0.1:5000' : 'localhost:5000')
                        def imageReference = "${registryHost}/quimia-api:${releaseTag}"

                        withCredentials([
                            string(
                                credentialsId: 'github-quimia-gitops-write',
                                variable: 'GITOPS_TOKEN'
                            )
                        ]) {
                            withEnv([
                                "RELEASE_TAG=${releaseTag}",
                                "GITOPS_BRANCH=${gitOpsBranch}",
                                "REGISTRY_HOST=${registryHost}",
                                "IMAGE_REFERENCE=${imageReference}"
                            ]) {
                                sh '''
                                    set +x
                                    set -eu

                                    gitops_dir=''
                                    askpass="$WORKSPACE/.gitops-askpass"
                                    pr_body="$WORKSPACE/.gitops-pr.json"
                                    merge_body="$WORKSPACE/.gitops-auto-merge.json"

                                    cleanup() {
                                        rm -f "$askpass" "$pr_body" "$merge_body"
                                        if [ -n "$gitops_dir" ] && [ -d "$gitops_dir" ]; then
                                            rm -rf "$gitops_dir"
                                        fi
                                    }
                                    trap cleanup EXIT

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

                                    cd "$WORKSPACE"
                                    pr_response="$(curl --fail --silent --show-error \
                                        -G \
                                        -H 'Accept: application/vnd.github+json' \
                                        -H 'X-GitHub-Api-Version: 2022-11-28' \
                                        -H "Authorization: Bearer ${GITOPS_TOKEN}" \
                                        --data-urlencode "head=Quimia-br:${GITOPS_BRANCH}" \
                                        --data-urlencode 'base=main' \
                                        --data-urlencode 'state=open' \
                                        https://api.github.com/repos/Quimia-br/quimia-gitops/pulls)"

                                    if printf '%s' "$pr_response" | grep -q '"node_id"'; then
                                        echo "PR existente encontrada para ${GITOPS_BRANCH}; reutilizando-a."
                                    else
                                        gitops_dir="$(mktemp -d "$WORKSPACE/gitops-release.XXXXXX")"
                                        git clone --depth 1 https://github.com/Quimia-br/quimia-gitops.git "$gitops_dir"
                                        cd "$gitops_dir"
                                        git config user.name "quimia-jenkins[bot]"
                                        git config user.email "quimia-jenkins[bot]@users.noreply.github.com"

                                        if git ls-remote --exit-code --heads origin "${GITOPS_BRANCH}" >/dev/null 2>&1; then
                                            git fetch --depth 1 origin "${GITOPS_BRANCH}"
                                            git switch -c "${GITOPS_BRANCH}" --track "origin/${GITOPS_BRANCH}"
                                        else
                                            git switch -c "${GITOPS_BRANCH}"
                                        fi

                                        overlay='apps/quimia-api/overlays/dev/kustomization.yaml'
                                        sed -i -E "s|^    newName:.*|    newName: ${REGISTRY_HOST}/quimia-api|" "$overlay"
                                        sed -i -E "s|^    newTag:.*|    newTag: ${RELEASE_TAG}|" "$overlay"
                                        test "$(
                                            grep -c '^    newName: ' "$overlay"
                                        )" -eq 1
                                        test "$(
                                            grep -c '^    newTag: ' "$overlay"
                                        )" -eq 1

                                        changed_files="$(git diff --name-only)"
                                        test "$changed_files" = "$overlay"
                                        if git diff --quiet -- "$overlay"; then
                                            echo "O GitOps já aponta para ${IMAGE_REFERENCE}; não há alteração para promover."
                                            exit 1
                                        fi

                                        git add -- "$overlay"
                                        git commit -m "ci: promove quimia-api ${RELEASE_TAG}"
                                        git push origin "HEAD:${GITOPS_BRANCH}"

                                        cd "$WORKSPACE"
                                        cat > "$pr_body" <<EOF
{"title":"ci: promove quimia-api ${RELEASE_TAG}","head":"${GITOPS_BRANCH}","base":"main","body":"Imagem publicada: \\`${IMAGE_REFERENCE}\\`.\\n\\nAlteração gerada pelo Jenkins após os checks da release."}
EOF

                                        pr_response="$(curl --fail --silent --show-error \\
                                            -X POST \\
                                            -H 'Accept: application/vnd.github+json' \\
                                            -H 'X-GitHub-Api-Version: 2022-11-28' \\
                                            -H "Authorization: Bearer ${GITOPS_TOKEN}" \\
                                            https://api.github.com/repos/Quimia-br/quimia-gitops/pulls \\
                                            --data-binary "@${pr_body}")"
                                    fi

                                    pr_node_id="$(printf '%s' "$pr_response" | sed -n 's/.*"node_id":[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1)"
                                    pr_url="$(printf '%s' "$pr_response" | sed -n 's/.*"html_url":[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1)"
                                    test -n "$pr_node_id"
                                    pr_number="$(printf '%s' "$pr_response" | grep -o '"number":[[:space:]]*[0-9]*' | head -n 1 | tr -cd '0-9')"
                                    test -n "$pr_number"
                                    test -n "$pr_url"
                                    printf '%s\\n' "$pr_url" > "$WORKSPACE/gitops-pr-url.txt"

                                    cat > "$merge_body" <<EOF
{"query":"mutation { enablePullRequestAutoMerge(input: {pullRequestId: \\\"${pr_node_id}\\\", mergeMethod: MERGE, commitHeadline: \\\"ci: promove quimia-api ${RELEASE_TAG}\\\"}) { pullRequest { url } } }"}
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

                                    if ! printf '%s' "$merge_response" | grep -Fq '"enablePullRequestAutoMerge"' || printf '%s' "$merge_response" | grep -Fq '"enablePullRequestAutoMerge":null'; then
                                        echo 'GitHub não confirmou o auto-merge da PR do GitOps.'
                                        exit 1
                                    fi

                                    pr_state_response="$(curl --fail --silent --show-error \\
                                        -H 'Accept: application/vnd.github+json' \\
                                        -H 'X-GitHub-Api-Version: 2022-11-28' \\
                                        -H "Authorization: Bearer ${GITOPS_TOKEN}" \\
                                        "https://api.github.com/repos/Quimia-br/quimia-gitops/pulls/${pr_number}")"

                                    if ! printf '%s' "$pr_state_response" | grep -Fq '"auto_merge":{' && ! printf '%s' "$pr_state_response" | grep -Fq '"merged":true'; then
                                        echo 'A PR do GitOps não ficou em auto-merge nem foi mesclada imediatamente.'
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
