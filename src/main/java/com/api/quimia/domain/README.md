# domain/

Modulos de negocio. Um subdiretorio por area de negocio do projeto.

Regras (do documento de arquitetura):
- Substantivo singular em ingles: `order`, `payment`, `catalog`.
- Conceito de negocio, nao tecnico: `payment`, nao `payment-processor`.
- Uma palavra quando possivel.
- Cada modulo tem raiz publica (`<Module>Gateway.java`, `dto/`, `event/`)
  e implementacao privada em `internal/`.
- Nada fora de `<module>/` pode importar de `<module>/internal/` de
  outro modulo — ArchUnit falha o build.
