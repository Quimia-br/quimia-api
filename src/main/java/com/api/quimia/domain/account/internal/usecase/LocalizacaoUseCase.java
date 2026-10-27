package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.dto.LocalizacaoRequest;
import com.api.quimia.domain.account.internal.dto.LocalizacaoResponse;
import com.api.quimia.domain.account.internal.model.LocalizacaoUsuario;
import com.api.quimia.domain.account.internal.persistence.LocalizacaoUsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Endereço 1:1 do usuário (`localizacao_usuario` tem UNIQUE em `id_usuario`). */
@Service
public class LocalizacaoUseCase {
    private static final int CEP_DIGITS = 8;
    private static final int CEP_PREFIX_DIGITS = 5;
    /** `localizacao_usuario.latitude/longitude` são NUMERIC(9,6). */
    private static final int COORDINATE_SCALE = 6;

    private final LocalizacaoUsuarioRepository localizacoes;

    public LocalizacaoUseCase(LocalizacaoUsuarioRepository localizacoes) {
        this.localizacoes = localizacoes;
    }

    @Transactional(readOnly = true)
    public LocalizacaoResponse current(UUID usuarioId) {
        return localizacoes.findByUsuarioId(usuarioId)
                .map(LocalizacaoUseCase::view)
                .orElseThrow(() -> new AccountException("not_found", 404));
    }

    /** Substitui o endereço inteiro: campos ausentes ficam nulos. */
    @Transactional
    public LocalizacaoResponse replace(UUID usuarioId, LocalizacaoRequest request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new AccountException("invalid_coordinates", 422);
        }
        LocalizacaoUsuario localizacao = localizacoes.findByUsuarioId(usuarioId).orElseGet(() -> {
            LocalizacaoUsuario created = new LocalizacaoUsuario();
            created.setUsuarioId(usuarioId);
            return created;
        });
        localizacao.setCep(formatCep(request.cep()));
        localizacao.setEstado(request.estado() == null ? null : request.estado().toUpperCase(Locale.ROOT));
        localizacao.setBairro(trimToNull(request.bairro()));
        localizacao.setRua(trimToNull(request.rua()));
        localizacao.setNumero(request.numero());
        localizacao.setComplemento(trimToNull(request.complemento()));
        localizacao.setLatitude(coordinate(request.latitude()));
        localizacao.setLongitude(coordinate(request.longitude()));
        return view(localizacoes.save(localizacao));
    }

    private static String formatCep(String cep) {
        if (cep == null) {
            return null;
        }
        String digits = cep.replace("-", "");
        if (digits.length() != CEP_DIGITS) {
            throw new AccountException("invalid_cep", 422);
        }
        return digits.substring(0, CEP_PREFIX_DIGITS) + "-" + digits.substring(CEP_PREFIX_DIGITS);
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value == null ? null : value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalizacaoResponse view(LocalizacaoUsuario localizacao) {
        return new LocalizacaoResponse(
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
