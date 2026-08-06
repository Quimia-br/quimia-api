# infra/web/

Utilitarios HTTP transversais que toda feature pode usar:
`PageResponse<T>`, `ApiError`, `CorrelationIdFilter`, interceptors,
conversores Jackson customizados.

Diferente de `domain/<module>/internal/web/`, que e o adaptador HTTP
especifico de um modulo. Aqui mora so o que e generico.
