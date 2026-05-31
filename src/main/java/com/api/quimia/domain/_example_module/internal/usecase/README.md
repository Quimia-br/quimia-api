# internal/usecase/

Operacoes de negocio do modulo. Uma classe por operacao
(Single Responsibility). Origem: Clean Architecture.

Convencao de nome: `<Verb><Noun>UseCase`
(`CreateOrderUseCase`, `CancelOrderUseCase`, `RefundPaymentUseCase`).

Regras:
- ArchUnit `usecases_live_in_internal_usecase` garante a localizacao.
- UseCase orquestra; nao implementa regra de entidade — entidades sao
  ricas e contem o comportamento (`order.cancel()`).
- Para falar com outros modulos, injeta o `<Module>Gateway` publico.
