# internal/dto/response/

DTOs de saida HTTP. Retornados por controllers para o cliente.

Convencao de nome: `<Noun>Response`
(`OrderResponse`, `CustomerResponse`).

Modelados para o cliente externo — pode incluir campos derivados,
formatados ou agregados. Nao confundir com DTO publico
(`<Noun>Summary` em `dto/` da raiz), que serve a outros modulos.
