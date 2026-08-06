# internal/event/ (opcional)

Eventos INTERNOS do modulo: publisher e listener vivem aqui. Para
eventos consumidos por outros modulos, use `event/` na raiz.

Convencao de nome:
- Evento: `<Noun><Verb-past>Event`.
- Listener: `<Noun>EventListener` ou anotacao `@TransactionalEventListener`
  direto no metodo.

Remover esta pasta se o modulo nao usa eventos internos.
