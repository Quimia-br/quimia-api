# internal/dto/

Payloads HTTP do modulo — contrato com o cliente externo (frontend,
mobile). Subpastas: `request/` e `response/`.

NAO confundir com `dto/` da raiz do modulo, que e o contrato com OUTROS
modulos. Mesmo que campos coincidam, os propositos sao distintos.

Quem consome:
- `request/` — entrada de controller (`@Valid CreateOrderRequest`).
- `response/` — saida de controller para o cliente HTTP.
