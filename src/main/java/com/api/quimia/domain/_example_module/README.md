# _example_module/

Modulo de referencia. O underscore sinaliza que e template — renomeie
para um substantivo singular de negocio (`order`, `payment`, etc.).

Estrutura:
- Raiz: contrato publico do modulo. Outros modulos podem importar daqui.
  - `<Module>Gateway.java` (interface) — porta sincrona.
  - `dto/` — DTOs publicos (`<Noun>Summary`, `<Noun>View`).
  - `event/` — eventos publicos consumidos por outros modulos.
- `internal/` — implementacao privada (package-private). Outros modulos
  NAO devem importar daqui.

Este modulo lista as pastas opcionais (`internal/client/`,
`internal/config/`, `internal/event/`) por ser referencia. Remova as que
seu modulo real nao usar.
