# Bruno — Catálogo

Coleção do catálogo, da estante e dos produtos da empresa. Cada etapa da
implementação acrescenta uma pasta.

1. Suba a API em `http://localhost:8080` e selecione o ambiente `local`.
2. Rode `auth/` na ordem: `A01` e `A02` (usuário), `A03` e `A04` (empresa).
   Os tokens ficam em `accessToken` e `empresaAccessToken`.

Para validar contra um banco descartável com o schema real, use
`scripts/local-validation/smoke.sh`, que faz as mesmas chamadas por `curl`.
