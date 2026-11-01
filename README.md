<div align="center">

# Quimia API

**O backend do Quimia: catálogo de produtos químicos, estante do usuário, análise de FDS e detecção de misturas perigosas.**

[![CI](https://github.com/Quimia-br/quimia-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Quimia-br/quimia-api/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Quimia-br/quimia-api?sort=semver)](https://github.com/Quimia-br/quimia-api/releases)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)](https://redis.io/)

[Visão geral](#visão-geral) · [Arquitetura](#arquitetura) · [Como rodar](#como-rodar) · [Rotas](#rotas) · [Segurança](#autenticação-e-autorização) · [Cache](#cache-com-redis) · [Testes](#testes) · [Docker](#docker) · [CI/CD](#cicd)

**Produção:** https://quimia.discloud.app · **Swagger:** `/swagger-ui.html` · **OpenAPI:** `/v3/api-docs`

</div>

---

API REST que atende o aplicativo móvel ([quimia-mobile](https://github.com/Quimia-br/quimia-mobile)) e o portal
web ([quimia-web](https://github.com/Quimia-br/quimia-web)).

## Visão geral

| Tema | Como é tratado |
| --- | --- |
| **Identidade** | Usuários do app entram por Firebase; empresas do portal, por e-mail e senha (BCrypt). A API emite JWT e cookie de renovação. |
| **Autorização** | Spring Security separa os perfis usuário, empresa e admin por rota. |
| **Catálogo e FDS** | CRUD de produtos da empresa e análise de FDS pelo [extrator](https://github.com/Quimia-br/quimia-extractor-ai), com pendências para revisão. |
| **Misturas** | Compatibilidade entre produtos com histórico e resumo. |
| **Erros** | Tratamento centralizado em `application/problem+json`. |
| **Cache** | Redis nas leituras mais frequentes, opcional por configuração. |
| **Contrato** | Swagger gerado do código; um teste falha se uma rota ficar sem documentação. |
| **Arquitetura** | Monólito modular com fronteiras garantidas por ArchUnit. |

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem / runtime | Java 21 (Temurin) |
| Framework | Spring Boot, Spring MVC |
| Persistência | Spring Data JPA / Hibernate sobre PostgreSQL |
| Segurança | Spring Security, JWT, Firebase (login do app), BCrypt |
| Cache | Redis (Spring Cache) |
| Documentação | springdoc-openapi (Swagger UI) |
| Build | Maven (wrapper `./mvnw`) |
| Qualidade | JUnit, MockMvc, ArchUnit, JaCoCo (mínimo de 80%) |
| Entrega | GitHub Actions, Docker, Discloud |

## Arquitetura

Monólito modular. O código é agrupado por área de negócio em `src/main/java/com/api/quimia/domain/<módulo>/`
e cada módulo esconde sua implementação em `internal/`. O build **falha** (ArchUnit) se um módulo acessar o
`internal/` de outro: a comunicação entre módulos passa apenas pelos *gateways* públicos.

| Módulo | Responsabilidade |
|---|---|
| `account` | Usuários, empresas, perfis, localização, autenticação |
| `catalog` | Produtos, marcas, produtos da empresa, análise de FDS, fotos |
| `storage` | Estante do usuário, cômodos, favoritos |
| `safety` | FDS, compatibilidade e incompatibilidades, primeiros socorros |
| `recommendation` | Misturas e resumo do histórico |
| `partner` | Pontos de descarte/parceiros e pontos salvos |
| `insights` | KPIs da empresa sobre o data mart |
| `assistant` | Integração com o agente Kemi |
| `infra` | Segurança, cache, OpenAPI, tratamento de exceções, persistência |

Dentro de cada módulo vale a divisão MVC em camadas:

```
internal/
├── web/          controllers (camada de apresentação)
├── usecase/      regras de negócio, um caso de uso por classe
├── persistence/  repositórios Spring Data JPA e gateways
├── model/        entidades JPA
├── dto/          contratos de entrada e saída
└── mapper/       conversão entre entidade e DTO
```

## Como rodar

Requisitos: **JDK 21** e acesso a um PostgreSQL com o schema do [quimia-bd](https://github.com/Quimia-br/quimia-bd).
O Maven não precisa estar instalado, o repositório traz o wrapper.

```bash
cp .env.sample .env      # preencha as variáveis (veja a tabela abaixo)
make run                 # sobe em http://localhost:8080 usando o banco do .env
make run-local           # sobe com um banco descartável, sem credenciais
make help                # lista os atalhos
```

Sem `make`:

```bash
./mvnw spring-boot:run
```

Conferência rápida: `GET http://localhost:8080/swagger-ui.html` abre a documentação, e
`GET /api/v1/usuarios/me` sem credenciais deve responder `401`.

## Variáveis de ambiente

Modelo completo em [`.env.sample`](.env.sample). Nunca versione o `.env`.

| Variável | Finalidade |
|---|---|
| `SERVER_PORT` | Porta HTTP (padrão 8080) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Conexão PostgreSQL. Use o usuário `quimia_api` |
| `QUIMIA_DB_POOL_MAXIMO`, `QUIMIA_DB_POOL_MINIMO_OCIOSO` | Tamanho do pool de conexões |
| `QUIMIA_JWT_PRIVATE_KEY_BASE64`, `QUIMIA_JWT_PUBLIC_KEY_BASE64`, `QUIMIA_JWT_KID` | Par de chaves que assina e valida os tokens |
| `APP_CORS_ALLOWED_ORIGINS` | Origens web autorizadas (lista separada por vírgula) |
| `QUIMIA_CACHE_ENABLED` | Liga o cache Redis (`false` por padrão) |
| `REDIS_URL` | URL do Redis |
| `QUIMIA_CACHE_TIMEOUT_MS` | Tempo máximo de uma operação de cache |
| `QUIMIA_CACHE_PREFIX` | Prefixo das chaves no Redis (`quimia:`) |
| `QUIMIA_EXTRATOR_URL`, `QUIMIA_EXTRATOR_TOKEN` | Serviço [quimia-extractor-ai](https://github.com/Quimia-br/quimia-extractor-ai). Sem URL, a leitura de FDS fica desligada |
| `SWAGGER_ENABLED` | `false` desliga o Swagger |

## Rotas

Prefixo comum: `/api/v1`. A lista completa, com parâmetros e códigos de resposta, está no Swagger.
A pasta [`.bruno/`](.bruno) traz uma coleção pronta para testar cada fluxo.

| Prefixo | Recurso |
|---|---|
| `/auth` | Login, cadastro, renovação e logout de usuários do app |
| `/empresas/auth`, `/empresas/me` | Cadastro, login, perfil e foto da empresa (portal) |
| `/empresas/me/produtos` | CRUD dos produtos da empresa |
| `/empresas/me/produtos/fds/analises` | Análise de FDS, correção e descarte de pendências |
| `/usuarios/me`, `/usuarios/me/foto` | Perfil e foto do usuário |
| `/produtos` | Consulta ao catálogo, busca por texto, código de barras e imagem |
| `/estante`, `/estante/produtos` | Estante do usuário, por data e favoritos |
| `/misturas` | Verificação de compatibilidade, histórico e resumo |
| `/pontos`, `/pontos/salvos` | Pontos de descarte próximos e pontos salvos |
| `/empresas/me/resumo` e rotas de KPI sob `/empresas/me` | Indicadores da empresa logada |

## Autenticação e autorização

- **Usuário do app:** identidade verificada pelo Firebase; a API emite o `accessToken` (JWT) e um cookie
  HttpOnly de renovação (`quimia_rt`).
- **Empresa do portal:** e-mail e senha (BCrypt), com a mesma emissão de token e cookie.
- **CSRF:** o refresh e o logout exigem o cookie `quimia_csrf` espelhado no header `X-CSRF-TOKEN`.
- **Autorização:** `SecurityConfig` separa os perfis (`USUARIO_PRINCIPAL`, `EMPRESA_PRINCIPAL`, `ADMIN`) por
  rota. Rotas protegidas respondem `401` sem token e `403` com perfil errado.
- Para testar no Swagger: faça login, copie o `accessToken` e use **Authorize**.

## Validação e tratamento de erros

- Entradas são validadas com Bean Validation (`@Valid`, `@NotBlank`, `@Size`, `@Email`) nos DTOs de
  `internal/dto`.
- `ProblemHandler` (`@RestControllerAdvice`) concentra o tratamento de exceções: validação, integridade do
  banco e exceções de cada módulo viram respostas `application/problem+json` com status HTTP adequado e um
  `detail` útil para o cliente.
- O teste `OpenApiContratoTest` falha se uma rota ficar sem documentação ou se um código de erro lançado não
  aparecer no Swagger.

## Cache com Redis

As leituras mais frequentes passam por um cache Redis (`@Cacheable` / `@CacheEvict`), configurado em
[`CacheConfig`](src/main/java/com/api/quimia/infra/config/CacheConfig.java):

| Cache | Onde é usado |
|---|---|
| Catálogo | Consulta de produto por id e por código de barras, listagens |
| Segurança | Dados de FDS, incompatibilidades e primeiros socorros |
| Pontos | Pontos de descarte por filtro |
| Insights | Agregados da empresa |

Escritas no catálogo invalidam as entradas afetadas. O cache é opcional: com `QUIMIA_CACHE_ENABLED=false` a
API funciona direto sobre o PostgreSQL. Testes: `CacheDasLeiturasWebTest` e `CachesNoRedisTest`.

## Banco de dados

PostgreSQL. A **fonte de verdade do schema** é o repositório [quimia-bd](https://github.com/Quimia-br/quimia-bd);
a API só valida (`ddl-auto=validate`) e nunca altera a estrutura. Regras equivalentes às *functions* do banco
(`fn_match_produtos`, `buscar_pontos_salvos`) estão refletidas em `MotorDeCompatibilidade` e
`PontoSalvoRepository`.

## Testes

```bash
./mvnw clean verify     # testes + ArchUnit + cobertura mínima de 80% (JaCoCo)
./mvnw test             # só os testes
```

Os testes de web usam MockMvc com banco em memória, sem rede. As regras de fronteira entre módulos são
verificadas por ArchUnit. A prática é escrever o teste do comportamento antes ou junto da implementação (TDD).

## Docker

O `Dockerfile` empacota o JAR já compilado em uma imagem `eclipse-temurin:21-jre`, executando como usuário
não root (uid 10001) na porta 8080.

```bash
./mvnw -B clean package -DskipTests
docker build -t quimia-api .
docker run --rm -p 8080:8080 --env-file .env quimia-api
```

A imagem é *stateless*, toda configuração vem de variáveis de ambiente, então serve para qualquer
orquestrador (Kubernetes, ECS, etc.) apenas informando as variáveis da seção acima e uma sonda HTTP em
`/api/v1/usuarios/me` (esperado `401`).

## CI/CD

Workflow [`ci.yml`](.github/workflows/ci.yml), executado em PRs para `main`, pushes em `main` e Releases:

| Job | O que faz |
|---|---|
| Build | Compila e empacota o JAR executável |
| Unit tests | JUnit e regras de arquitetura (ArchUnit) |
| Coverage gate | `mvn verify` com mínimo de 80% de cobertura |
| Discloud artifact | Gera o ZIP de implantação (`app.jar` + `discloud.config`) |
| Deploy to Discloud | Só em Release publicada, com aprovação manual no Environment `discloud` |
| PR title | [`pr-title.yml`](.github/workflows/pr-title.yml) exige título no padrão Conventional Commits |

Para publicar: crie uma Release no GitHub e aprove o deploy no Environment `discloud`.

## Fluxo de contribuição

- Uma branch por ticket (`QUI-<id>`), aberta a partir de `main`.
- Commits e títulos de PR em [Conventional Commits](docs/conventional-commits-padrao.md).
- Todo PR usa o [template](.github/pull_request_template.md) e precisa de revisão aprovada antes do merge.
- Convenção de branch e PR: [docs/branch-e-pull-request-padrao.md](docs/branch-e-pull-request-padrao.md).

## Padrão de projeto

**Null Object + Factory** na leitura de FDS.
[`ExtratorFdsConfiguration`](src/main/java/com/api/quimia/domain/catalog/internal/fds/ExtratorFdsConfiguration.java)
é a fábrica: conforme a configuração, entrega `HttpExtratorFds` (chama o extrator real) ou
`ExtratorFdsDesligado` (objeto nulo que implementa a mesma interface `ExtratorFds` sem fazer nada). O restante
da aplicação usa `ExtratorFds` sem `if` de "extrator existe?", e o ambiente local sobe sem o serviço externo.

## Repositórios relacionados

| Repositório | Papel |
|---|---|
| [quimia-mobile](https://github.com/Quimia-br/quimia-mobile) | Aplicativo Android |
| [quimia-web](https://github.com/Quimia-br/quimia-web) | Portal web das empresas |
| [quimia-agent](https://github.com/Quimia-br/quimia-agent) | Assistente Kemi (multiagente) |
| [quimia-extractor-ai](https://github.com/Quimia-br/quimia-extractor-ai) | Extração de dados de FDS |
| [quimia-bd](https://github.com/Quimia-br/quimia-bd) | Banco de dados, data mart e documentação |
