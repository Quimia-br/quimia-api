package com.api.quimia.domain.account.internal.persistence;

import com.api.quimia.domain.account.LocalizacaoGateway;
import com.api.quimia.domain.account.dto.LocalizacaoView;
import com.api.quimia.domain.account.internal.model.LocalizacaoUsuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaLocalizacaoGateway implements LocalizacaoGateway {
    private final LocalizacaoUsuarioRepository localizacoes;

    public JpaLocalizacaoGateway(LocalizacaoUsuarioRepository localizacoes) {
        this.localizacoes = localizacoes;
    }

    @Override
    public Optional<LocalizacaoView> doUsuario(UUID usuarioId) {
        return localizacoes.findByUsuarioId(usuarioId).map(JpaLocalizacaoGateway::view);
    }

    private static LocalizacaoView view(LocalizacaoUsuario localizacao) {
        return new LocalizacaoView(
                localizacao.getCep(),
                localizacao.getEstado(),
                localizacao.getBairro(),
                localizacao.getRua(),
                localizacao.getNumero(),
                localizacao.getComplemento(),
                localizacao.getLatitude(),
                localizacao.getLongitude());
    }
}
