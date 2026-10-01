# Bruno — Account/Auth

Coleção alinhada ao contrato da decisão 21 (`docs/context/account-api-contract.md`).
A coleção anterior, com o fluxo web do usuário e refresh rotativo no banco,
está em `_archive/.bruno/account-auth/`.

## Pré-requisitos

1. API em `http://localhost:8080`, com o PostgreSQL provisionado pelo DDL de
   referência, chaves JWT e `APP_AUTH_HMAC_SECRET` configurados.
2. Abra esta pasta como coleção no Bruno e selecione o ambiente `local`.

## Usuário (app) — pasta `usuario/`

Ordem: `U01 Register` → `U02 Mobile Login` → `U03`…`U07`.

- **Recuperação:** `U08 Forgot Password` guarda `challengeToken`. Preencha
  `recoveryCode` com o código de 4 dígitos recebido por email (exige
  `APP_EMAIL_SENDER_MODE=smtp` ou `resend`) e rode `U09` e `U10`. Depois disso,
  `U02` usa a nova senha.
- **Firebase:** `U11` precisa de um `firebaseIdToken` real emitido pelo app e de
  `FIREBASE_PROJECT_ID` na API.
- **Logout:** `U12` não revoga nada no servidor. As sessões não têm estado, e o
  refresh só cai quando expira ou quando a senha muda.

## Empresa (portal web) — pasta `empresa/`

Ordem: `E01 Register` → `E02 Login` → `E03`…`E05` → `E08 Logout`.

- O login grava `empresaRefreshToken` e `csrfToken` a partir de `Set-Cookie`.
  Refresh e logout enviam os dois cookies e `X-CSRF-Token`.
- O CNPJ precisa ser único: troque `empresaCnpj` antes de repetir `E01` no
  mesmo banco.
- **Recuperação:** `E06` envia um link de 24 horas. Copie o `token` do link
  para `empresaResetToken` e rode `E07`.

## Negativos — pasta `negative/`

São independentes. `empresa-*` precisam de um login de empresa feito antes.
`duplicate-register` documenta uma lacuna conhecida: o `409` revela emails
existentes.
