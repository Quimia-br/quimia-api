# internal/web/

Adaptador HTTP do modulo: controllers, request validators, response
builders. Nome `web` cobre REST, GraphQL, SSE e WebSocket.

Convencao de nome:
- Controller: `<Module>Controller` (ex: `OrderController`).

Regras:
- ArchUnit `controllers_live_in_internal_web` garante que classes
  anotadas com `@RestController`/`@Controller` morem aqui.
- Controller NUNCA chama outro controller — comunicacao entre modulos
  e via Gateway ou eventos.
