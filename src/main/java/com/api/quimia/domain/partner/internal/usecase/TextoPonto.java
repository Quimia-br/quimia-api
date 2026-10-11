package com.api.quimia.domain.partner.internal.usecase;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalização de bairro, estado e CEP. Valores em branco viram `null` (critério ausente). */
final class TextoPonto {
    private static final Pattern MARCAS_DE_ACENTO = Pattern.compile("\\p{M}");
    private static final Pattern UF = Pattern.compile("[A-Z]{2}");
    private static final Pattern CEP_COM_OITO_DIGITOS = Pattern.compile("\\d{8}");
    private static final int PREFIXO_DO_CEP = 5;

    private TextoPonto() {}

    static String semAcento(String texto) {
        if (texto == null) {
            return "";
        }
        String decomposto = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD);
        return MARCAS_DE_ACENTO.matcher(decomposto).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Bairro para comparação, ou `null` se em branco. */
    static String bairro(String bairro) {
        String normalizado = semAcento(bairro);
        return normalizado.isEmpty() ? null : normalizado;
    }

    /** UF em maiúsculas; lança `422 estado_invalido` se não tiver exatamente duas letras. */
    static String estado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String uf = estado.trim().toUpperCase(Locale.ROOT);
        if (!UF.matcher(uf).matches()) {
            throw new PartnerException("estado_invalido", 422);
        }
        return uf;
    }

    /** CEP só com os 8 dígitos; lança `422 cep_invalido` se o formato não casar. */
    static String cep(String cep) {
        if (cep == null || cep.isBlank()) {
            return null;
        }
        String digitos = cep.trim().replace("-", "");
        if (!CEP_COM_OITO_DIGITOS.matcher(digitos).matches()) {
            throw new PartnerException("cep_invalido", 422);
        }
        return digitos;
    }

    /** CEP do banco para comparação: 8 dígitos ou `null` quando ausente ou fora do formato. */
    static String cepDoBanco(String cep) {
        if (cep == null) {
            return null;
        }
        String digitos = cep.trim().replace("-", "");
        return CEP_COM_OITO_DIGITOS.matcher(digitos).matches() ? digitos : null;
    }

    static String prefixo(String cepComOitoDigitos) {
        return cepComOitoDigitos.substring(0, PREFIXO_DO_CEP);
    }
}
