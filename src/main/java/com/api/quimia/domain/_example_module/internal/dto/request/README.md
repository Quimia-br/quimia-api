# internal/dto/request/

DTOs de entrada HTTP. Recebidos por controllers com `@RequestBody` +
`@Valid`.

Convencao de nome: `<Verb><Noun>Request`
(`CreateOrderRequest`, `UpdateCustomerRequest`).

Use anotacoes de Bean Validation (`@NotBlank`, `@Email`, `@Size`...) —
validacao acontece na fronteira HTTP.
