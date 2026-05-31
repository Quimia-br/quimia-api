# internal/persistence/

Acesso a dados: `JpaRepository`, `MongoRepository`, Specifications,
queries customizadas. Tudo que toca banco — nao so o repositorio.

Convencao de nome: `<Entity>Repository` (`OrderRepository`).

Regras:
- Repositorios NAO sao expostos para fora do modulo. Quem precisa de
  dados consome via `<Module>Gateway` publico que retorna DTO publico.
- Entidade JPA nunca vaza para outros modulos — gateway sempre converte
  para DTO publico.
