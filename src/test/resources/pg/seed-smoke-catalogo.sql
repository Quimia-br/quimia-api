-- Dados do smoke (camada C) do catálogo. Aplicado só no banco descartável do smoke.sh.
WITH marca_seed AS (
    INSERT INTO marca (nome, logo_url) VALUES ('Smoke Marca', 'https://cdn.test/smoke.png') RETURNING id
), empresa_seed AS (
    INSERT INTO empresa (nome, email, senha, ativo) VALUES ('Smoke Seed', 'seed@smoke.test', 'x', TRUE) RETURNING id
), produto_seed AS (
    INSERT INTO produto (nome, id_marca, tipo_produto, cod_barras, descricao)
    SELECT dados.nome, marca_seed.id, dados.tipo, dados.cod_barras, dados.descricao
      FROM marca_seed,
           (VALUES ('Limpa Vidros Smoke', 'limpeza_geral', '7892000000001', 'Sem vínculo com empresa'),
                   ('Água Sanitária Smoke', 'alvejante', '789200000002', 'Vínculo ativo; código UPC-A'),
                   ('Produto Oculto Smoke', 'outro', '7892000000004', 'Único vínculo desativado'))
           AS dados(nome, tipo, cod_barras, descricao)
    RETURNING id, cod_barras
)
INSERT INTO empresa_produto (id_empresa, id_produto, ativo)
SELECT empresa_seed.id, produto_seed.id, produto_seed.cod_barras <> '7892000000004'
  FROM empresa_seed, produto_seed
 WHERE produto_seed.cod_barras <> '7892000000001';
