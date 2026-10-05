package com.api.quimia;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
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
        jdbc.update("DELETE FROM fds_composto");
        jdbc.update("DELETE FROM fds");
        jdbc.update("DELETE FROM substancia_classe_quimica");
        jdbc.update("DELETE FROM substancia_sinonimo");
        jdbc.update("DELETE FROM substancia");
        jdbc.update("DELETE FROM classe_quimica");
        jdbc.update("DELETE FROM estante_produto");
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
