package com.api.quimia.domain.account.internal.model;

import com.api.quimia.domain.account.NivelAcesso;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter
public class NivelAcessoConverter implements AttributeConverter<NivelAcesso, String> {
    @Override
    public String convertToDatabaseColumn(NivelAcesso nivelAcesso) {
        return nivelAcesso == null ? null : nivelAcesso.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public NivelAcesso convertToEntityAttribute(String valor) {
        return valor == null ? null : NivelAcesso.valueOf(valor.toUpperCase(Locale.ROOT));
    }
}
