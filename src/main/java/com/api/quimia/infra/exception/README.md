# infra/exception/

Tratamento global de erros:
- `GlobalExceptionHandler` (`@RestControllerAdvice`) padroniza a
  resposta HTTP para qualquer excecao.
- Hierarquia base de excecoes: `DomainException` (abstrata),
  `NotFoundException`, `ValidationException`, `ConflictException`,
  `UnauthorizedException`.

Convencao de nome: `<Noun><Reason>Exception`
(`OrderNotFoundException`, `PaymentDeclinedException`) — definidas nos
modulos, capturadas e traduzidas aqui.
