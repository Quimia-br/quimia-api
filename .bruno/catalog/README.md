# Bruno — Catálogo

Coleção do catálogo, da estante e dos produtos da empresa. Cada etapa da
implementação acrescenta uma pasta.

1. Suba a API em `http://localhost:8080` e selecione o ambiente `local`.
2. Rode `auth/` na ordem: `A01` e `A02` (usuário), `A03` e `A04` (empresa).
   Os tokens ficam em `accessToken` e `empresaAccessToken`.
3. `produtos/` (app, token de usuário): listar com busca, detalhe e busca por
   código de barras. Ajuste `termo` e `codBarras` no ambiente para produtos do seu banco.
4. `estante/` (app, token de usuário): guardar o `produtoId` obtido em `produtos/`,
   listar a estante e remover.
5. `portal/` (portal, token de empresa): listar, cadastrar (multipart, com `imagem` opcional), alterar e
   deletar. O cadastro gera um código de barras novo a cada execução.
6. `safety/` (app, token de usuário): resumo do rótulo (produto na estante), composição do produto,
   substâncias da estante e detalhe da substância. Todos são GET; os dados vêm do banco.

Para validar contra um banco descartável com o schema real, use
`scripts/local-validation/smoke.sh`, que faz as mesmas chamadas por `curl`.
