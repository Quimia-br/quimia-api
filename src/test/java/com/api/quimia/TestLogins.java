package com.api.quimia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Obtém tokens pelos fluxos reais de cadastro e login, como o app e o portal fazem. */
public final class TestLogins {
    private static final String SENHA = "SenhaTeste2026";

    private TestLogins() {}

    public static String usuario(MockMvc mvc, String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isCreated());
        return accessToken(mvc.perform(post("/api/v1/auth/mobile/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}")));
    }

    /** O CNPJ precisa ser válido e não usado por outro teste. */
    public static String empresa(MockMvc mvc, String email, String cnpj) throws Exception {
        return empresa(mvc, "Empresa Teste", email, cnpj);
    }

    /** O nome da empresa vira o nome da marca dos produtos que ela cadastra. */
    public static String empresa(MockMvc mvc, String nome, String email, String cnpj) throws Exception {
        mvc.perform(post("/api/v1/empresas/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"cnpj\":\"" + cnpj + "\",\"email\":\"" + email
                                + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isCreated());
        return accessToken(mvc.perform(post("/api/v1/empresas/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}")));
    }

    private static String accessToken(ResultActions login) throws Exception {
        String json = login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}
