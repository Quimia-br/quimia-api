# internal/model/

Modelo de dominio do modulo: entidades (`@Entity`, `@Document`),
Value Objects, enums.

Convencao de nome: substantivo singular (`Order`, `Payment`).

Principio: entidades ricas, nao anemicas. Logica em metodos da entidade
(`order.cancel()`, `wallet.debit(amount)`), nao em "services" externos.
