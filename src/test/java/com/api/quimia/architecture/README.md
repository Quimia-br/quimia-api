# architecture/

Testes de arquitetura (ArchUnit) — build gates das fronteiras do
template. Sem isso, `internal/` e DTO publico viram convencoes
quebraveis.

`ArchitectureTest.java` cobre:
1. `internal/` privado entre modulos.
2. Gateways publicos sao interfaces.
3. UseCases moram em `..internal.usecase..`.
4. Controllers moram em `..internal.web..`.
5. Sufixo `Impl` banido.
6. `domain/` nao depende de `infra/`.
7. Camadas internas de um modulo sao aciclicas.

Roda como teste unitario comum no `mvn test`/`mvn verify`.
