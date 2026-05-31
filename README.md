# quimia

Scaffold modular Java/Spring. Estrutura agrupa codigo por area de negocio
(`domain/<module>/`) com fronteiras `internal/` privadas garantidas por
ArchUnit no build.

Detalhes de convencao, nomenclatura e fronteiras estao em
[`Arquitetura Template Modular Java Spring.md`](./Arquitetura%20Template%20Modular%20Java%20Spring.md).

## Comandos

```
mvn clean verify     # compila + roda testes (inclui ArchUnit)
mvn spring-boot:run  # sobe a aplicacao em http://localhost:8080
mvn test             # so testes
```

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
