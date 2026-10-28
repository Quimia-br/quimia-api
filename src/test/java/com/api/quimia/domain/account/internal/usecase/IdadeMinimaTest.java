package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** O cadastro e o perfil aceitam a partir de 16 anos completos. */
class IdadeMinimaTest {
    @Test
    void aceitaQuemFaz16AnosHojeEQuemNaoInformouANascimento() {
        LocalDate hoje = LocalDate.now();

        assertThatCode(() -> RegistrarUseCase.requireMinimumAge(hoje.minusYears(16))).doesNotThrowAnyException();
        assertThatCode(() -> RegistrarUseCase.requireMinimumAge(hoje.minusYears(40))).doesNotThrowAnyException();
        assertThatCode(() -> RegistrarUseCase.requireMinimumAge(null)).doesNotThrowAnyException();
    }

    @Test
    void recusaQuemAindaNaoCompletou16Anos() {
        LocalDate hoje = LocalDate.now();

        for (LocalDate nascimento : new LocalDate[] {hoje.minusYears(16).plusDays(1), hoje.minusYears(15), hoje.minusYears(10)}) {
            assertThatThrownBy(() -> RegistrarUseCase.requireMinimumAge(nascimento))
                    .isInstanceOfSatisfying(AccountException.class, erro -> {
                        assertThat(erro.code()).isEqualTo("underage");
                        assertThat(erro.status()).isEqualTo(422);
                    });
        }
    }
}
