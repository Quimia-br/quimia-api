# Bruno — Account/Auth

Coleção baseada nos endpoints implementados em `domain/account`.

## Pré-requisitos

1. Subir a API em `http://localhost:8080` com PostgreSQL previamente
   provisionado conforme `docs/context/account-database-contract.md` e chaves
   JWT configuradas. A API valida o schema e não cria nem altera tabelas.
2. Abrir esta pasta como coleção no Bruno.
3. Selecionar o ambiente `local`.
4. Executar as requisições na ordem numérica.

O cadastro cria um email único e grava `userEmail` como variável de runtime.

## Cadastro e recuperação

O cadastro não envia confirmação de email e a conta pode entrar imediatamente.
As rotas de verificação e reenvio foram removidas.

A solicitação de recuperação responde `202` com corpo vazio para contas
existentes e desconhecidas. O modo padrão de email é `noop`, então nenhum
código chega ao usuário até Resend e um domínio remetente verificado serem
configurados. O código não é escrito em logs nem retornado pela API.

`APP_AUTH_RECOVERY_CODE_PEPPER` é obrigatório em todos os ambientes e deve ter
ao menos 32 bytes. Para entrega real, configure `APP_EMAIL_SENDER_MODE=resend`,
`RESEND_API_KEY` e `APP_EMAIL_FROM`, com domínio remetente verificado. Não
coloque esses segredos no repositório.

Com Resend ativo, execute `11 Forgot Password`, copie o código recebido para
`recoveryCode`, depois execute `12 Verify Recovery Code` e `13 Reset Password`.
O `resetToken` é guardado como variável Bruno e, após o reset, `userPassword`
passa a usar `recoveryPassword`.

## Refresh web e mobile

- O fluxo principal usa cookie + `X-CSRF-Token`, como o cliente web.
- Os arquivos em `alternatives/` fazem login, refresh e logout mobile pelo
  corpo JSON, sem extrair tokens de `Set-Cookie`. Limpe o cookie jar do Bruno
  antes de executar esses exemplos.
- Execute apenas uma variante de refresh por token, pois cada uso rotaciona e
  invalida o token anterior.
- O login web emite `quimia_rt` e `quimia_csrf`; o script captura ambos.
  O valor CSRF precisa ser reenviado no cookie e em `X-CSRF-Token`.
- O login mobile usa `/api/v1/auth/mobile/login` e devolve `refreshToken` somente
  no JSON. O nome do cookie web padrão pode ser alterado por configuração;
  ajuste os exemplos web caso use outro nome.

## Execução sugerida

```text
01 Register
05 Login
06 Get Me
07 Update Me
08 Refresh Web
09 Reuse Previous Refresh
[opcional: executar known-issues/refresh-family-revocation]
[05 Login novamente para criar nova família]
10 Logout Web
```

Para recuperação, execute `11 Forgot Password` → informe manualmente o código
recebido em `recoveryCode` → `12 Verify Recovery Code` → `13 Reset Password`.
Depois, `05 Login` usa a senha redefinida. O cadastro não precisa passar por
confirmação de email.

Os casos em `negative/` são independentes e podem ser executados separadamente.
Os arquivos em `known-issues/` foram preservados como regressões dos defeitos
anteriores e agora devem passar. Execute `missing-refresh` com o cookie jar
limpo. A pasta manteve o nome antigo para não apagar arquivos existentes.

## Limitações desta coleção

- A recuperação pode exercitar a resposta genérica `202`, mas validar o código
  exige `APP_EMAIL_SENDER_MODE=resend`, secrets configurados e acesso à caixa
  de email. No modo `noop`, não há código disponível para completar o fluxo.
- `duplicate-register` continua mostrando `409 email_in_use`. Esse retorno
  enumera emails; a documentação não o classifica como anti-enumeração.
- Atraso progressivo, `429` e limite de IP ainda não estão implementados.
