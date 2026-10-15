package com.api.quimia.domain.safety.dto;

import java.util.List;

public record SubstanciaView(
        Integer id,
        String nome,
        String casNumero,
        String descricao,
        List<String> sinonimos,
        List<ClasseQuimicaView> classes) {}
