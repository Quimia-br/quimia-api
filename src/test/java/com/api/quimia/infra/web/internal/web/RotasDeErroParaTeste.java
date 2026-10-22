package com.api.quimia.infra.web.internal.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Rotas só de teste para provocar os erros que nenhuma rota da API provoca de propósito. */
@RestController
@RequestMapping("/api/v1/teste-erros")
public class RotasDeErroParaTeste {
    public static final String SEGREDO = "detalhe-interno-que-nao-pode-vazar";

    @GetMapping("/obrigatorio")
    Map<String, String> obrigatorio(@RequestParam String valor) {
        return Map.of("valor", valor);
    }

    @GetMapping("/falha")
    Map<String, String> falha() {
        throw new IllegalStateException(SEGREDO);
    }

    @GetMapping("/conflito")
    Map<String, String> conflito() {
        throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @GetMapping("/acesso-negado")
    Map<String, String> acessoNegado() {
        throw new AccessDeniedException("negado dentro do controller");
    }

    @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> json() {
        return Map.of("ok", "sim");
    }
}
