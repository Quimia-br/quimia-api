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

-- Segurança química (Etapa 5). A FDS da Água Sanitária entra por raw_json: o trigger do banco
-- materializa fds_composto, resolvendo por CAS ("Hipoclorito"), por sinônimo ("Soda cáustica") e
-- deixando sem substância o que não resolve ("Fragrância").
INSERT INTO classe_quimica (nome, descricao) VALUES
    ('Corrosivo', 'Corrói tecidos e metais'),
    ('Oxidante', 'Favorece a combustão');
INSERT INTO substancia (nome_canonico, cas_numero, descricao) VALUES
    ('Hipoclorito de sódio', '7681-52-9', 'Alvejante e desinfetante'),
    ('Hidróxido de sódio', '1310-73-2', NULL);
INSERT INTO substancia_sinonimo (id_substancia, sinonimo, sinonimo_normalizado)
SELECT id, 'Soda cáustica', 'soda caustica' FROM substancia WHERE cas_numero = '1310-73-2';
INSERT INTO substancia_classe_quimica (id_substancia, id_classe_quimica)
SELECT s.id, c.id FROM substancia s, classe_quimica c
 WHERE (s.cas_numero = '7681-52-9' AND c.nome IN ('Corrosivo', 'Oxidante'))
    OR (s.cas_numero = '1310-73-2' AND c.nome = 'Corrosivo');
INSERT INTO fds (id_produto, versao, data_atualizacao, ativo, resumo_simplificado, raw_json)
SELECT id, 'v1', DATE '2024-03-01', TRUE, 'Não misture com ácidos nem amônia.',
       '{"secoes":{"03":{"compostos":[
           {"nome":"Hipoclorito de sódio","cas_numero":"7681-52-9","concentracao_min":"2.5","concentracao_max":"3"},
           {"nome":"Soda cáustica","cas_numero":null,"concentracao_min":"0.5"},
           {"nome":"Fragrância","cas_numero":null}]}}}'::jsonb
  FROM produto WHERE cod_barras = '789200000002';
