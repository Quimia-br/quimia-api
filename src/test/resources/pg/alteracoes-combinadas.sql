-- Alterações combinadas com o DBA que ainda não estão no repositório quimia-bd.
-- Aplicado DEPOIS do DDL de quimia-bd/, só em bancos descartáveis de teste.
-- Quando o DBA publicar cada alteração no quimia-bd, remova a linha daqui
-- (scripts/local-validation/sync-schema.sh mostra a diferença).
--
-- Nenhuma pendente: quimia-bd@271eb40 já inclui marca.logo_url,
-- fds.resumo_simplificado, UNIQUE(estante.id_usuario) e estante_produto.id_comodo.
SELECT 1;
