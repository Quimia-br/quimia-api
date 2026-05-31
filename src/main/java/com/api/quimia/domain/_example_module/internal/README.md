# internal/

Implementacao privada do modulo. Tudo aqui deve ser `package-private`
sempre que possivel — classes `public` so quando o framework exigir
(controllers, @Configuration, entidades JPA carregadas por reflection).

ArchUnit (`internal_is_private_across_modules`) impede que classes de
outros modulos importem daqui. Importacoes dentro do mesmo modulo sao
permitidas.

Subpastas padrao: `web/`, `usecase/`, `persistence/`, `model/`, `dto/`,
`mapper/`. Subpastas opcionais: `event/`, `client/`, `config/` — remover
se nao usadas.

A implementacao do `<Module>Gateway` mora aqui com prefixo tecnico:
`Jpa<Module>Gateway`, `Mongo<Module>Gateway`, etc. Sufixo `Impl` e banido.
