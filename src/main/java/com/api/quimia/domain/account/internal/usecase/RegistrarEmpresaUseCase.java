package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.EmpresaSummary;
import com.api.quimia.domain.account.internal.dto.EmpresaRegisterRequest;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarEmpresaUseCase {
    private final EmpresaRepository empresas;
    private final PasswordEncoder passwords;
    private final AuthenticationAuditRecorder audit;

    public RegistrarEmpresaUseCase(
            EmpresaRepository empresas, PasswordEncoder passwords, AuthenticationAuditRecorder audit) {
        this.empresas = empresas;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional
    public EmpresaSummary execute(EmpresaRegisterRequest request) {
        String email = Emails.normalize(request.email());
        String cnpj = Cnpj.normalize(request.cnpj());
        if (!Cnpj.isValid(cnpj)) {
            throw new AccountException("invalid_cnpj", 422);
        }
        // `empresa.email` não tem UNIQUE no schema: a unicidade é garantida aqui.
        if (empresas.existsByNormalizedEmail(email)) {
            throw new AccountException("email_in_use", 409);
        }
        if (empresas.existsByCnpj(cnpj)) {
            throw new AccountException("cnpj_in_use", 409);
        }
        PasswordPolicy.validate(request.senha());
        Empresa empresa = new Empresa();
        empresa.setNome(request.nome().trim());
        empresa.setEmail(email);
        empresa.setCnpj(cnpj);
        empresa.setAtivo(true);
        empresa.setSenha(passwords.encode(request.senha()));
        empresas.save(empresa);
        audit.record("registration_succeeded", SessionIssuer.principalOf(empresa));
        return new EmpresaSummary(empresa.getId(), empresa.getNome(), empresa.getEmail());
    }
}
