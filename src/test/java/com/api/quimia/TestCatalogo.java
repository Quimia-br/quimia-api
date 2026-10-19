package com.api.quimia;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * Monta dados de catálogo direto no banco, sem informar ids (o schema real usa
 * `GENERATED ALWAYS AS IDENTITY`). Funciona no H2 e no PostgreSQL.
 */
public final class TestCatalogo {
    private final JdbcTemplate jdbc;

    public TestCatalogo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Apaga o catálogo e as estantes; empresas e usuários de teste ficam a cargo de cada classe. */
    public void limpar() {
        if (postgres()) {
            jdbc.update("DELETE FROM sinonimo_pendente");
        }
        jdbc.update("DELETE FROM historico_match");
        jdbc.update("DELETE FROM ponto_parceiro");
        jdbc.update("DELETE FROM incompatibilidade_regra");
        jdbc.update("DELETE FROM fds_composto");
        jdbc.update("DELETE FROM fds");
        jdbc.update("DELETE FROM substancia_classe_quimica");
        jdbc.update("DELETE FROM substancia_sinonimo");
        jdbc.update("DELETE FROM substancia");
        jdbc.update("DELETE FROM classe_quimica");
        jdbc.update("DELETE FROM estante_produto");
        jdbc.update("DELETE FROM comodo");
        jdbc.update("DELETE FROM estante");
        jdbc.update("DELETE FROM empresa_produto");
        jdbc.update("DELETE FROM produto");
        jdbc.update("DELETE FROM marca");
    }

    public int marca(String nome, String logoUrl) {
        return insert("INSERT INTO marca (nome, logo_url) VALUES (?, ?)", nome, logoUrl);
    }

    public int empresa(String nome, String email) {
        return insert("INSERT INTO empresa (nome, email, senha, ativo) VALUES (?, ?, 'x', TRUE)", nome, email);
    }

    public int produto(String nome, int marca, String tipo, String codBarras, String descricao) {
        return insert(
                "INSERT INTO produto (nome, id_marca, tipo_produto, cod_barras, descricao) VALUES (?, ?, ?, ?, ?)",
                nome, marca, tipo, codBarras, descricao);
    }

    /** `ativo` é SQL literal (TRUE, FALSE ou NULL) para exercitar o NULL do schema. */
    public void vinculo(int empresa, int produto, String ativo) {
        jdbc.update("INSERT INTO empresa_produto (id_empresa, id_produto, ativo) VALUES (?, ?, " + ativo + ")",
                empresa, produto);
    }

    /**
     * FDS sem os campos do parser. No PostgreSQL o trigger do banco exige `raw_json` não nulo, então
     * vai um JSON vazio (não gera compostos); os compostos entram por {@link #composto}.
     */
    public int fds(int produto, String versao, LocalDate dataAtualizacao, boolean ativo, String resumo) {
        String rawJson = postgres() ? ", raw_json) VALUES (?, ?, ?, ?, ?, '{}'::jsonb)" : ") VALUES (?, ?, ?, ?, ?)";
        return insert(
                "INSERT INTO fds (id_produto, versao, data_atualizacao, ativo, resumo_simplificado" + rawJson,
                produto, versao, dataAtualizacao, ativo, resumo);
    }

    /** FDS com o JSON do extrator em `raw_json`; no PostgreSQL o trigger do banco processa o JSON. */
    public int fdsComConteudo(int produto, String versao, LocalDate dataAtualizacao, boolean ativo, String rawJson) {
        String json = postgres() ? "CAST(? AS jsonb)" : "? FORMAT JSON";
        return insert(
                "INSERT INTO fds (id_produto, versao, data_atualizacao, ativo, raw_json) VALUES (?, ?, ?, ?, " + json + ")",
                produto, versao, dataAtualizacao, ativo, rawJson);
    }

    /** `substancia` nulo cria o composto não resolvido pelo parser. */
    public int composto(int fds, Integer substancia, String min, String max, String unidade) {
        return insert(
                "INSERT INTO fds_composto (id_fds, id_substancia, concentracao_min, concentracao_max,"
                        + " unidade_concentracao) VALUES (?, ?, ?, ?, ?)",
                fds, substancia, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                unidade);
    }

    public int substancia(String nome, String cas, String descricao) {
        return insert("INSERT INTO substancia (nome_canonico, cas_numero, descricao) VALUES (?, ?, ?)", nome, cas, descricao);
    }

    public int sinonimo(int substancia, String texto) {
        return insert(
                "INSERT INTO substancia_sinonimo (id_substancia, sinonimo, sinonimo_normalizado) VALUES (?, ?, ?)",
                substancia, texto, texto.toLowerCase());
    }

    public int classe(String nome, String descricao) {
        return insert("INSERT INTO classe_quimica (nome, descricao) VALUES (?, ?)", nome, descricao);
    }

    public int substanciaClasse(int substancia, int classe) {
        return insert(
                "INSERT INTO substancia_classe_quimica (id_substancia, id_classe_quimica) VALUES (?, ?)",
                substancia, classe);
    }

    /**
     * Regra de incompatibilidade. Cada lado é uma substância OU uma classe: passe `null` no outro
     * (o banco real tem CHECK de exatamente um por lado).
     */
    public int regra(
            Integer substanciaA, Integer classeA, Integer substanciaB, Integer classeB, String severidade,
            String descricaoRisco, boolean ativo) {
        return insert(
                "INSERT INTO incompatibilidade_regra (id_substancia_a, id_classe_a, id_substancia_b, id_classe_b,"
                        + " severidade, descricao_risco, ativo) VALUES (?, ?, ?, ?, ?, ?, ?)",
                substanciaA, classeA, substanciaB, classeB, severidade, descricaoRisco, ativo);
    }

    public UUID usuarioId(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    /** Guarda o produto na estante do usuário (cria a estante se faltar), sem passar pela API. */
    public void guardar(UUID usuario, int produto) {
        Integer estantes = jdbc.queryForObject("SELECT count(*) FROM estante WHERE id_usuario = ?", Integer.class, usuario);
        if (estantes == null || estantes == 0) {
            jdbc.update("INSERT INTO estante (id_usuario, nome) VALUES (?, 'Minha estante')", usuario);
        }
        jdbc.update(
                "INSERT INTO estante_produto (id_produto, id_usuario, id_estante)"
                        + " SELECT ?, ?, e.id FROM estante e WHERE e.id_usuario = ?",
                produto, usuario, usuario);
    }

    public int comodo(UUID usuario, String nome, String icone) {
        return insert("INSERT INTO comodo (id_usuario, nome, icone_comodo) VALUES (?, ?, ?)", usuario, nome, icone);
    }

    /** Liga o produto (já na estante do usuário) ao cômodo. */
    public void noComodo(UUID usuario, int produto, int comodo) {
        jdbc.update("UPDATE estante_produto SET id_comodo = ? WHERE id_usuario = ? AND id_produto = ?",
                comodo, usuario, produto);
    }

    /** Grava a mistura com o par normalizado (menor id primeiro), como o CHECK do banco exige. */
    public int historicoMatch(
            UUID usuario, int produtoA, int produtoB, String resultado, String severidade, String descricaoRisco,
            Integer regra) {
        return insert(
                "INSERT INTO historico_match (id_usuario, id_produto_a, id_produto_b, resultado, id_regra,"
                        + " severidade, descricao_risco, data_consulta) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                usuario, Math.min(produtoA, produtoB), Math.max(produtoA, produtoB), resultado, regra, severidade,
                descricaoRisco);
    }

    /** `ativo` é SQL literal (TRUE, FALSE ou NULL), como em {@link #vinculo}. */
    public int pontoParceiro(String nome, String tipo, String cep, String estado, String bairro, String ativo) {
        return insert(
                "INSERT INTO ponto_parceiro (nome, tipo, cep, estado, bairro, ativo) VALUES (?, ?, ?, ?, ?, " + ativo + ")",
                nome, tipo, cep, estado, bairro);
    }

    /** O schema real tem trigger e tabelas de curadoria que o H2 gerado pelas entidades não tem. */
    public boolean postgres() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
    }

    private int insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        return keys.getKeyAs(Number.class).intValue();
    }
}
