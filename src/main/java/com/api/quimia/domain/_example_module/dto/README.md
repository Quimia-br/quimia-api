# dto/ (publico)

DTOs do contrato publico do modulo — vocabulario compartilhado com
outros modulos.

Convencao de nome: sufixo `Summary`, `View` ou `Snapshot`
(`CustomerSummary`, `ProductView`, `OrderSnapshot`).

NAO confundir com `internal/dto/`, que sao payloads HTTP. Mesmo que os
campos coincidam, os propositos sao distintos: este DTO e desenhado para
o que outros modulos precisam saber, sem vazar comportamento.

Prefira `record` imutavel.
