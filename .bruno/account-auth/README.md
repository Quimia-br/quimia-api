# Bruno — Account/Auth

Coleção baseada nos endpoints implementados em `domain/account`.

## Pré-requisitos

1. Subir a API em `http://localhost:8080` com PostgreSQL, migrations e chaves
   JWT configuradas.
2. Abrir esta pasta como coleção no Bruno.
3. Selecionar o ambiente `local`.
4. Executar as requisições na ordem numérica.

O cadastro cria um email único e grava `userEmail` como variável de runtime.

## Limite da verificação de email

O `NoOpEmailVerificationSender` atual descarta o token bruto e registra somente
o UUID do usuário. O banco armazena apenas o hash, portanto não é possível
recuperar o token pela API ou pelo PostgreSQL.

Antes de executar `04 - Verify Email`, preencha `verificationToken` com o token
recebido por um provedor real ou fornecido por uma fixture local controlada.
Não exponha o token em commits ou logs de produção.

## Refresh web e mobile

- O fluxo principal usa cookie + `X-CSRF-Token`, como o cliente web.
- Os arquivos em `alternatives/` fazem login, refresh e logout mobile pelo
  corpo JSON, sem extrair tokens de `Set-Cookie`. Limpe o cookie jar do Bruno
  antes de executar esses exemplos.
- Execute apenas uma variante de refresh por token, pois cada uso rotaciona e
  invalida o token anterior.
- O login web emite `quimia_rt` e `quimia_csrf`; o script captura ambos.
  O valor CSRF precisa ser reenviado no cookie e em `X-CSRF-Token`.
- O login mobile usa `/auth/mobile/login` e devolve `refreshToken` somente
  no JSON. O nome do cookie web padrão pode ser alterado por configuração;
  ajuste os exemplos web caso use outro nome.

## Execução sugerida

```text
01 Register
02 Login Before Verification
03 Resend Verification
[preencher verificationToken]
04 Verify Email
05 Login
06 Get Me
07 Update Me
08 Refresh Web
09 Reuse Previous Refresh
[opcional: executar known-issues/refresh-family-revocation]
[05 Login novamente para criar nova família]
10 Logout Web
```

Os casos em `negative/` são independentes e podem ser executados separadamente.
Os arquivos em `known-issues/` foram preservados como regressões dos defeitos
anteriores e agora devem passar. Execute `missing-refresh` com o cookie jar
limpo. A pasta manteve o nome antigo para não apagar arquivos existentes.

## Problemas reproduzíveis pela coleção

- A coleção depende de um token de verificação fornecido por sender real ou
  fixture local controlada. Com o sender `NoOp`, o fluxo completo Bruno fica
  parcialmente manual.
- `duplicate-register` continua mostrando `409 email_in_use`. Esse retorno
  enumera emails; a documentação não o classifica como anti-enumeração.
- Atraso progressivo, `429` e limite de IP ainda não estão implementados.
