# infra/config/

`@Configuration` globais da aplicacao: `JpaConfig`, `MongoConfig`,
`OpenApiConfig`, `JacksonConfig`, `CorsConfig`, `SecurityConfig`.

Regra: se a configuracao e especifica de um modulo, ela vive DENTRO do
modulo (`domain/<module>/internal/config/`), nao aqui.
