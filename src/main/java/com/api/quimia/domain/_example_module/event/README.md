# event/ (publico)

Eventos publicos do modulo — consumidos por listeners de OUTROS modulos
via `@TransactionalEventListener`.

Convencao de nome: `<Noun><Verb-past>Event` (`OrderCreatedEvent`,
`PaymentFailedEvent`).

Pasta opcional — remova se o modulo nao publica eventos para fora.
Eventos internos (publicador e consumidor no mesmo modulo) vivem em
`internal/event/`.
