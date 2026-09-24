package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.VerificarEmailUseCase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    private RegistrarUseCase registrar;

    @Autowired
    private VerificarEmailUseCase verificar;

    @Autowired
    private AccountTestConfig.TokenProbe sender;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void webFlowUsesCookiesAndCsrf() throws Exception {
        MvcResult register = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(
                                "Web User", "web@example.com", "senha-forte-web", LocalDate.of(1990, 1, 1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("web@example.com"))
                .andExpect(jsonPath("$.nivelAcesso").value("USUARIO"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn();
        assertThat(register.getResponse().getContentAsString()).doesNotContain("senha-forte-web");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"web@example.com\",\"senha\":\"senha-forte-web\"}"))
                .andExpect(status().isForbidden());

        verificar.execute(sender.lastToken());

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"web@example.com\",\"senha\":\"senha-forte-web\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("quimia_rt"))
                .andExpect(cookie().exists("quimia_csrf"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String access = mapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
        assertThat(login.getResponse().getContentAsString()).doesNotContain("senha-forte-web");
        Cookie refresh = login.getResponse().getCookie("quimia_rt");
        Cookie csrfCookie = login.getResponse().getCookie("quimia_csrf");
        String csrf = csrfCookie.getValue();

        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("web@example.com"));

        mvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + access + "x"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, csrfCookie)
                        .header("X-CSRF-Token", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh.getValue() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid_transport"));
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, csrfCookie)
                        .header("X-CSRF-Token", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid_transport"));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, csrfCookie)
                        .header("X-CSRF-Token", csrf))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("quimia_rt"))
                .andExpect(cookie().exists("quimia_csrf"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("quimia_rt");
        Cookie rotatedCsrf = refreshed.getResponse().getCookie("quimia_csrf");
        String rotatedCsrfValue = rotatedCsrf.getValue();
        JsonNode refreshedBody = mapper.readTree(refreshed.getResponse().getContentAsString());
        String rotatedAccess = refreshedBody.get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh, csrfCookie)
                        .header("X-CSRF-Token", csrf))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + rotatedAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Web Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Web Renamed"));

        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(rotated, rotatedCsrf)
                        .header("X-CSRF-Token", rotatedCsrfValue)
                        .header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("quimia_rt", 0))
                .andExpect(cookie().maxAge("quimia_csrf", 0));
    }

    @Test
    void mobileRefreshUsesBody() throws Exception {
        registrar.execute(new RegisterRequest(
                "Mobile User", "mobile@example.com", "senha-forte-mob", LocalDate.of(1991, 2, 2)));
        verificar.execute(sender.lastToken());

        MvcResult login = mvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mobile@example.com\",\"senha\":\"senha-forte-mob\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("quimia_rt"))
                .andExpect(cookie().doesNotExist("quimia_csrf"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        JsonNode body = mapper.readTree(login.getResponse().getContentAsString());
        String refresh = body.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/mobile/refresh")
                        .cookie(new Cookie("quimia_rt", "unexpected-browser-cookie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid_transport"));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("quimia_rt"))
                .andExpect(cookie().doesNotExist("quimia_csrf"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        JsonNode refreshedBody = mapper.readTree(refreshed.getResponse().getContentAsString());

        mvc.perform(post("/api/v1/auth/mobile/logout")
                        .header("Authorization", "Bearer " + refreshedBody.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshedBody.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("quimia_rt"))
                .andExpect(cookie().doesNotExist("quimia_csrf"));

        mvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(
                                "X", "bad", "curta", LocalDate.of(1990, 1, 1)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mobile@example.com\",\"senha\":\"errada-123456\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshWithoutCookieOrBodyReturnsInvalidRefresh() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("invalid_refresh"));
    }
}
