package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import com.api.quimia.domain.account.internal.persistence.LocalizacaoUsuarioRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class AccountWebTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UsuarioRepository users;

    @Autowired
    private LocalizacaoUsuarioRepository localizacoes;

    @Autowired
    private EmpresaRepository empresas;

    @BeforeEach
    void clean() {
        localizacoes.deleteAll();
        users.deleteAll();
        empresas.deleteAll();
    }

    @Test
    void forgotPasswordReturnsSameShapeWhetherEmailExists() throws Exception {
        registerUsuario("existing-recovery@example.com", "senhaExistente1");

        JsonNode unknown = json(mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted())
                .andReturn());
        JsonNode existing = json(mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"existing-recovery@example.com\"}"))
                .andExpect(status().isAccepted())
                .andReturn());

        assertThat(unknown.size()).isEqualTo(2).isEqualTo(existing.size());
        assertThat(unknown.get("expiresIn").asLong()).isEqualTo(existing.get("expiresIn").asLong());
        assertThat(existing.get("challengeToken").asText()).isNotBlank();
        assertThat(unknown.get("challengeToken").asText()).isNotBlank();
    }

    @Test
    void mobileFlowUsesBodyTokensProfileAndAddress() throws Exception {
        MvcResult register = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Mobile User","email":"mobile@example.com","senha":"senhaMobile1",
                                 "dataNasc":"1991-02-02",
                                 "localizacao":{"cep":"06000-000","estado":"SP","cidade":"Osasco","bairro":"Centro","numero":100}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nivelAcesso").value("USUARIO"))
                .andReturn();
        assertThat(register.getResponse().getContentAsString()).doesNotContain("senhaMobile1");

        JsonNode login = json(mvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mobile@example.com\",\"senha\":\"senhaMobile1\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("quimia_rt"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("mobile@example.com"))
                .andReturn());
        String access = login.get("accessToken").asText();

        JsonNode refreshed = json(mvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn());
        assertThat(refreshed.get("accessToken").asText()).isNotBlank();

        mvc.perform(get("/api/v1/usuarios/me/localizacao").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("06000-000"))
                .andExpect(jsonPath("$.rua").doesNotExist());
        mvc.perform(put("/api/v1/usuarios/me/localizacao")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cep\":\"01310100\",\"estado\":\"sp\",\"rua\":\"Av. Paulista\",\"numero\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("01310-100"))
                .andExpect(jsonPath("$.estado").value("SP"))
                .andExpect(jsonPath("$.bairro").doesNotExist());
        mvc.perform(patch("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Mobile Renamed\",\"fotoUrl\":\"https://cdn.example.com/me.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Mobile Renamed"))
                .andExpect(jsonPath("$.fotoUrl").value("https://cdn.example.com/me.png"));
        mvc.perform(patch("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fotoUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/mobile/logout")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/usuarios").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidatesPayloadAndPasswordRule() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"bad\",\"senha\":\"curta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Sem Numero\",\"email\":\"nonum@example.com\",\"senha\":\"somenteletras\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("weak_password"));
        mvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonum@example.com\",\"senha\":\"errada123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void empresaWebFlowUsesCookiesCsrfAndSeparateProfile() throws Exception {
        mvc.perform(post("/api/v1/empresas/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Omo\",\"cnpj\":\"4i84329\",\"email\":\"omo@example.com\",\"senha\":\"senhaOmo123\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("invalid_cnpj"));
        mvc.perform(post("/api/v1/empresas/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Omo","cnpj":"11.222.333/0001-81","email":"omo@example.com","senha":"senhaOmo123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("omo@example.com"));

        MvcResult login = mvc.perform(post("/api/v1/empresas/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"omo@example.com\",\"senha\":\"senhaOmo123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("quimia_rt"))
                .andExpect(cookie().path("quimia_rt", "/api/v1/empresas/auth"))
                .andExpect(cookie().httpOnly("quimia_rt", true))
                .andExpect(cookie().exists("quimia_csrf"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.empresa.nome").value("Omo"))
                .andReturn();
        String access = json(login).get("accessToken").asText();
        Cookie refresh = login.getResponse().getCookie("quimia_rt");
        Cookie csrf = login.getResponse().getCookie("quimia_csrf");

        mvc.perform(get("/api/v1/empresas/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value("11222333000181"))
                .andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/empresas/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/empresas/auth/refresh")
                        .cookie(refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid_transport"));
        mvc.perform(post("/api/v1/empresas/auth/refresh")
                        .cookie(refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("quimia_rt"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(patch("/api/v1/empresas/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Omo Brasil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Omo Brasil"));

        mvc.perform(post("/api/v1/empresas/auth/logout")
                        .cookie(refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("quimia_rt", 0))
                .andExpect(cookie().maxAge("quimia_csrf", 0));
    }

    @Test
    void empresaRefreshWithoutCookieIsInvalidAndForgotPasswordIsGeneric() throws Exception {
        mvc.perform(post("/api/v1/empresas/auth/refresh").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("invalid_refresh"));
        mvc.perform(post("/api/v1/empresas/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).isEqualTo("{}"));
    }

    private void registerUsuario(String email, String senha) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isCreated());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
