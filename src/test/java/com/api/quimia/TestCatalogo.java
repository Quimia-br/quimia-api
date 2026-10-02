package com.api.quimia;

import java.sql.PreparedStatement;
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
