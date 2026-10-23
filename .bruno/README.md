# Bruno — Quimia API

Uma coleção só, com todas as rotas. Cada requisição tem, na aba **Docs**, o que faz, o que rodar
antes e as respostas possíveis.

## Como usar

1. Suba a API em `http://localhost:8080`.
2. No Bruno: **Open Collection** e escolha esta pasta (`.bruno`). Selecione o ambiente **local**.
3. Rode a pasta **00 - Começar aqui** inteira (botão direito, **Run**). Ela cria o usuário e a
   empresa de teste e faz login nos dois. Pode repetir sem problema.
4. Pronto: qualquer requisição das outras pastas já funciona. Os tokens e os ids (`produtoId`,
   `portalProdutoId`, `substanciaId`, `codBarras`) são guardados sozinhos pelas respostas.

Para uma ordem natural do app: **03 Catálogo** (01) → **04 Estante** (01) → **05 Segurança**.
Para o portal: **06 Portal** (02) cadastra um produto e já deixa o `produtoId` pronto para o app.

## Pastas

| Pasta | Quem | O que tem |
|---|---|---|
| `00-comecar-aqui` | — | Registrar e entrar (usuário e empresa) |
| `01-usuario` | app | Perfil, endereço, renovar sessão, sair, Firebase |
| `02-empresa` | portal | Perfil, renovar sessão, sair |
| `03-catalogo` | app | Listar, detalhe, busca por código de barras |
| `04-estante` | app | Guardar, listar, remover |
| `05-seguranca` | app | Resumo do rótulo, substâncias do produto e da estante, detalhe, "Como se proteger?" |
| `06-portal` | portal | Listar, cadastrar (com e sem imagem), alterar, trocar imagem, remover, Home, Estatísticas, WebKemi |
| `07-recuperacao-de-senha` | app e portal | Código por email (usuário) e link por email (empresa) |
| `08-comodos` | app | Criar, listar, detalhar, adicionar e remover produtos, cômodo do produto |
| `09-misturas` | app | Misturar dois produtos, histórico, detalhe |
| `10-pontos` | app | Pontos de compra e descarte |
| `11-kemi` | app | Perguntar ao Kemi |
| `90-casos-de-erro` | — | Respostas de erro esperadas (401, 403, 404, 409, 415, 422...) |

Dentro de cada pasta os arquivos seguem `NN-nome.bru`, na ordem em que fazem sentido.

## O que depende de configuração na API

| Requisições | Precisa de |
|---|---|
| `07-recuperacao-de-senha` | Envio de email: `APP_EMAIL_SENDER_MODE=smtp` (Gmail) ou `resend`, `APP_EMAIL_FROM` e as credenciais do modo. No ambiente `local`, troque `recoveryEmail` por um email seu. Cole o código recebido em `recoveryCode` (e o `token` do link da empresa em `empresaResetToken`). Sem envio configurado (`noop`), nada chega. |
| `06-portal` 03 e 05 (imagem) | `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` e `CLOUDINARY_API_SECRET`. Sem elas respondem 503. O arquivo enviado é `fixtures/imagem-produto.png`. |
| `11-kemi` e `06-portal` 13 | `QUIMIA_AGENTE_URL` (quimia-agent no ar). Sem ela respondem 503. |
| `08-comodos` e `09-misturas` | Dois produtos na estante (`04 / 01` com dois ids, depois `04 / 02`). A avaliação de risco só sai de `nao_avaliado` com FDS, compostos e regras de incompatibilidade no banco. |
| `01-usuario` 07 (Firebase) | `FIREBASE_PROJECT_ID` na API e um `firebaseIdToken` real no ambiente. |
| `03` a `05` com dados | Produtos no banco. Sem produtos, rode `06-portal` 02. O resumo e as substâncias só mostram algo se houver FDS no banco. |

## Observações

- As requisições com `Authorization` usam `accessToken` (usuário) ou `empresaAccessToken`
  (empresa). O access vale 15 minutos: se uma resposta der 401, rode `00 / 02` ou `00 / 04` de novo.
- A empresa usa cookies de refresh e CSRF, guardados pelo login (`00 / 04`).
- `90-casos-de-erro` é independente do resto, mas várias precisam de um login feito antes (indicado
  em cada Docs).
- Contra um banco descartável com o schema real, `scripts/local-validation/smoke.sh` faz as mesmas
  chamadas por `curl`.
