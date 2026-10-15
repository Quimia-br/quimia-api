package com.api.quimia.domain.catalog.internal.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Código de barras do rótulo: só dígitos, sem limite de tamanho (o catálogo tem códigos fora do padrão EAN). */
public final class CodigoBarras {
    private static final Pattern SO_DIGITOS = Pattern.compile("\\d+");
    private static final int UPC_A = 12;
    private static final int EAN_13 = 13;

    private CodigoBarras() {}

    /**
     * Código sem os espaços (U+0020) das pontas, ou `null` quando vazio. Lança se tiver algo além de dígitos,
     * inclusive tabulação e outros caracteres de controle nas pontas, que `trim()` removeria em silêncio.
     */
    public static String normalizar(String codigo) {
        if (codigo == null) {
            return null;
        }
        String valor = semEspacosNasPontas(codigo);
        if (valor.isEmpty()) {
            return null;
        }
        if (!SO_DIGITOS.matcher(valor).matches()) {
            throw new IllegalArgumentException("codigo_barras_invalido");
        }
        return valor;
    }

    private static String semEspacosNasPontas(String texto) {
        int inicio = 0;
        int fim = texto.length();
        while (inicio < fim && texto.charAt(inicio) == ' ') {
            inicio++;
        }
        while (fim > inicio && texto.charAt(fim - 1) == ' ') {
            fim--;
        }
        return texto.substring(inicio, fim);
    }

    /**
     * Formas do mesmo produto: leitores devolvem UPC-A (12 dígitos) ou o EAN-13 com `0` inicial.
     * O código recebido vem primeiro.
     */
    public static Set<String> equivalentes(String codigo) {
        Set<String> codigos = new LinkedHashSet<>();
        codigos.add(codigo);
        if (codigo.length() == UPC_A) {
            codigos.add("0" + codigo);
        }
        if (codigo.length() == EAN_13 && codigo.startsWith("0")) {
            codigos.add(codigo.substring(1));
        }
        return codigos;
    }

    /** Representante único das formas equivalentes (UPC-A vira EAN-13). */
    public static String canonico(String codigo) {
        return codigo.length() == UPC_A ? "0" + codigo : codigo;
    }
}
