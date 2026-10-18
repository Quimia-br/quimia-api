package com.api.quimia.domain.account.internal.usecase;

/** Valida o ID token emitido pelo Firebase Authentication (Google e Microsoft no app). */
public interface ExternalIdentityVerifier {
    /** Lança {@link AccountException} quando o token é inválido ou o provedor não está configurado. */
    ExternalIdentity verify(String idToken);

    /** `picture` é a URL da foto do provedor (claim `picture`); pode vir nula, como em contas Microsoft. */
    record ExternalIdentity(String email, boolean emailVerified, String name, String picture) {}
}
