# quimia

Scaffold modular Java/Spring. Estrutura agrupa codigo por area de negocio
(`domain/<module>/`) com fronteiras `internal/` privadas garantidas por
ArchUnit no build.

Detalhes de convencao, nomenclatura e fronteiras estao em
[`Arquitetura Template Modular Java Spring.md`](./Arquitetura%20Template%20Modular%20Java%20Spring.md).

## Requisitos

- JDK 21 (Temurin recomendado)
- Maven 3.9+

## Comandos

```
mvn clean verify              # compila + testes (inclui ArchUnit + JaCoCo 80%)
mvn spring-boot:run           # sobe a aplicacao em http://localhost:8080
mvn test                      # so testes
mvn dependency-check:check    # auditoria de vulnerabilidades (lento no 1o run)
```

## CI, checks e deploy

O GitHub Actions executa `.github/workflows/ci.yml` em PRs para `main`,
pushes em `main` e Releases publicadas. O workflow também pode ser iniciado
manualmente para validar os gates sem fazer deploy. Cada check publica um
resumo com seu objetivo, critério de aprovação, resultado e onde investigar
uma falha; o check `CI summary` consolida a execução.

- **Build** — compila e empacota o JAR executável Spring Boot.
- **Unit tests** — executa JUnit/Surefire e as regras de arquitetura ArchUnit.
- **Coverage gate** — executa `mvn verify`; JaCoCo exige cobertura de
  instruções de pelo menos 80%.
- **Security gate (OWASP)** — bloqueia vulnerabilidades não suprimidas com
  CVSS ≥ 7.0; o relatório HTML fica nos artefatos da execução.
- **Discloud artifact** — valida os testes do helper e gera um ZIP com apenas
  `app.jar` e `discloud.config` na raiz.
- **Deploy to Discloud** — só roda ao publicar uma Release; exige aprovação
  no Environment `discloud`, envia o ZIP e verifica que
  `/api/v1/usuarios/me` responde HTTP 401 sem credenciais.
- **PR title** — `.github/workflows/pr-title.yml` exige título Conventional
  Commits; a descrição em português é convenção revisada por pessoas, não uma
  detecção automática de idioma.

Depois que os novos checks aparecerem no primeiro PR, configure a proteção de
`main` para exigir `Build`, `Unit tests`, `Coverage gate`,
`Security gate (OWASP)`, `Discloud artifact`, `CI summary` e `PR title`.
Remova da regra os checks antigos de Jenkins; o deploy não é um check exigido
para PR, pois só roda em Release publicada.

Para acelerar o OWASP Dependency-Check, configure `NVD_API_KEY` como secret
do repositório. Sem ela o scanner continua executando, mas pode sofrer rate
limit e demorar mais.

### Preparar o primeiro deploy

Crie o Environment `discloud` em **Settings → Environments** e configure
aprovação manual por reviewer. Adicione o secret `DISCLOUD_TOKEN`. Para o
primeiro deploy, deixe `DISCLOUD_APP_ID` ausente: se `GET /v2/user` confirmar
`user.apps: []`, o workflow cria o primeiro app com `apps.create`. Depois,
consulte o ID interno no painel da Discloud ou em `user.apps` na resposta de
`GET /v2/user` e cadastre-o como secret `DISCLOUD_APP_ID`; Releases seguintes
atualizam esse app. Se já houver qualquer app na conta e o ID estiver ausente,
o workflow falha sem criar outro app. Esse ID interno não é o subdomínio
`quimia-api` configurado no `discloud.config`.

A primeira Release é o bootstrap do app. Assim que `apps.create` criar a
aplicação, configure nela as variáveis de runtime listadas abaixo e cadastre
seu ID como `DISCLOUD_APP_ID`. Se essa primeira execução falhar no smoke check
porque o app ainda não iniciou com a configuração, reexecute o job de deploy
falho pela aba **Actions**; ele atualizará o app criado, sem criar uma
duplicata, e repetirá o smoke check. Não publique outra Release só para repetir
esse bootstrap.

O `discloud.config` já define `ID=quimia-api`. Se o cadastro/reserva do
subdomínio continuar retornando HTTP 400, a criação inicial também pode ser
recusada pela Discloud; esse erro da plataforma precisa ser resolvido antes de
o smoke check em `https://quimia-api.discloud.app` passar.

As variáveis de runtime da API devem ser configuradas na Discloud, separadas
dos secrets do Actions: `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
`QUIMIA_JWT_KID`, `QUIMIA_JWT_PRIVATE_KEY_BASE64`,
`QUIMIA_JWT_PUBLIC_KEY_BASE64` e `SERVER_PORT=8080`. Não coloque os valores
no repositório ou no pacote. O Flyway roda no startup; não conecte um banco
externo até revisar e autorizar a aplicação das migrations.

O ícone dos checks padrão do GitHub Actions não é configurável por workflow.
Uma identidade visual própria pode ser considerada depois usando uma GitHub
App que publique checks; a imagem da App é configurável pela plataforma
([documentação](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/creating-a-custom-badge-for-your-github-app)).

## Como usar este scaffold

1. Renomeie o base package `com.api.quimia` para o seu (se for
   reaproveitar como template em outro projeto).
2. Renomeie `domain/_example_module/` para o primeiro modulo real
   (substantivo singular em ingles: `order`, `payment`, etc.).
3. Apague pastas opcionais do modulo (`internal/client/`,
   `internal/config/`, `internal/event/`, `event/`) se nao for usar.
4. Replique a estrutura para cada novo modulo de negocio.
5. Mantenha `ArchitectureTest` verde — a fronteira `internal/` e
   regra de build, nao convencao.
