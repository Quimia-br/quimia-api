package com.api.quimia.domain.partner;

import com.api.quimia.domain.account.LocalizacaoGateway;
import com.api.quimia.domain.account.dto.LocalizacaoView;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Lê `localizacao_usuario` direto do banco, como o gateway real de `account` fará. */
@TestConfiguration
public class PartnerDoubles {
    @Bean
    @Primary
    LocalizacaoGateway localizacaoDuble(JdbcTemplate jdbc) {
        return usuarioId -> {
            List<LocalizacaoView> linhas = jdbc.query(
                    "SELECT cep, estado, bairro, rua, numero, complemento FROM localizacao_usuario WHERE id_usuario = ?",
                    (rs, i) -> new LocalizacaoView(
                            rs.getString("cep"), rs.getString("estado"), rs.getString("bairro"),
                            rs.getString("rua"), (Integer) rs.getObject("numero"), rs.getString("complemento")),
                    usuarioId);
            return linhas.stream().findFirst();
        };
    }
}
