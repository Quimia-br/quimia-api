# internal/mapper/

Conversoes entre entidade, DTO interno (HTTP) e DTO publico (entre
modulos).

Convencao de nome: `<Source>To<Target>Mapper` ou `<Module>Mapper`.

- Prefira MapStruct para mapeamentos nao-triviais (geracao em compile
  time, sem reflection em runtime).
- Para mapeamentos com 3-4 campos, construtor do record e mais legivel.
- A pasta se chama `mapper/` (nao `converter/`) por alinhamento com a
  convencao de nome do MapStruct.
