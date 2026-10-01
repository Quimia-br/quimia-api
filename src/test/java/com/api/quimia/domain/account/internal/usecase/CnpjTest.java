package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CnpjTest {
    @Test
    void acceptsNumericAndAlphanumericCnpjWithValidCheckDigits() {
        assertThat(Cnpj.isValid(Cnpj.normalize("11.222.333/0001-81"))).isTrue();
        assertThat(Cnpj.isValid(Cnpj.normalize("12.abc.345/01de-35"))).isTrue();
    }

    @Test
    void rejectsWrongCheckDigitsMalformedAndRepeatedValues() {
        assertThat(Cnpj.isValid(Cnpj.normalize("11.222.333/0001-82"))).isFalse();
        assertThat(Cnpj.isValid(Cnpj.normalize("12ABC34501DE3A"))).isFalse();
        assertThat(Cnpj.isValid(Cnpj.normalize("4i84329"))).isFalse();
        assertThat(Cnpj.isValid("00000000000000")).isFalse();
    }

    @Test
    void normalizeStripsPunctuationAndUppercases() {
        assertThat(Cnpj.normalize(" 12.abc.345/01de-35 ")).isEqualTo("12ABC34501DE35");
    }
}
