package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.EmpresaView;
import com.api.quimia.domain.account.internal.dto.UpdateEmpresaRequest;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilEmpresaUseCase {
    private final EmpresaRepository empresas;

    public PerfilEmpresaUseCase(EmpresaRepository empresas) {
        this.empresas = empresas;
    }

    @Transactional(readOnly = true)
    public EmpresaView current(Integer id) {
        return view(find(id));
    }

    @Transactional
    public EmpresaView update(Integer id, UpdateEmpresaRequest request) {
        Empresa empresa = find(id);
        if (request.nome() != null) {
            empresa.setNome(request.nome().trim());
        }
        if (request.email() != null) {
            String email = Emails.normalize(request.email());
            if (!email.equals(Emails.normalize(empresa.getEmail())) && empresas.existsByNormalizedEmail(email)) {
                throw new AccountException("email_in_use", 409);
            }
            empresa.setEmail(email);
        }
        if (request.fotoUrl() != null) {
            empresa.setFotoUrl(request.fotoUrl());
        }
        return view(empresa);
    }

    private Empresa find(Integer id) {
        return empresas.findById(id).orElseThrow(() -> new AccountException("not_found", 404));
    }

    private static EmpresaView view(Empresa empresa) {
        return new EmpresaView(
                empresa.getId(),
                empresa.getNome(),
                empresa.getEmail(),
                empresa.getCnpj(),
                empresa.getFotoUrl(),
                empresa.isAtiva());
    }
}
