# infra/

Infraestrutura tecnica transversal — fundacao que toda feature usa mas
nao pertence a nenhuma area de negocio.

Regra: se um arquivo aqui responde a regra de NEGOCIO, esta no lugar
errado. Mova para o modulo correspondente em `domain/`.

Subpastas obrigatorias no scaffold: `config/`, `exception/`, `web/`.
Opcionais (criar quando o projeto pedir): `security/`, `messaging/`,
`observability/`, `audit/`.

ArchUnit (`domain_does_not_depend_on_infra`) impede que codigo de
`domain/` dependa de `infra/`.
