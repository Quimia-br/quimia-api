package com.api.quimia.domain.catalog.internal.fds;

/** Usado quando `QUIMIA_EXTRATOR_URL` não foi configurada: só falha se alguém enviar uma FDS. */
final class ExtratorFdsDesligado implements ExtratorFds {
    @Override
    public String extrair(byte[] pdf) {
        throw new ExtratorFdsException(ExtratorFdsException.Motivo.INDISPONIVEL);
    }
}
