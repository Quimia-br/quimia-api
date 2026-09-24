package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.VerificarEmailUseCase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        classes = {com.api.quimia.Application.class, AccountTestConfig.class},
        properties = "app.auth.cookie-name=custom_refresh")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class AccountCookieConfigurationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrarUseCase registrar;

    @Autowired
    private VerificarEmailUseCase verificar;

    @Autowired
    private AccountTestConfig.TokenProbe sender;

    @Test
    void configuredRefreshCookieNameIsUsedAcrossWebFlow() throws Exception {
        registrar.execute(new RegisterRequest(
                "Cookie User", "cookie@example.com", "senha-forte-cookie", LocalDate.of(1990, 1, 1)));
        verificar.execute(sender.lastToken());

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cookie@example.com\",\"senha\":\"senha-forte-cookie\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("custom_refresh"))
                .andExpect(cookie().doesNotExist("quimia_rt"))
                .andReturn();

        Cookie refresh = login.getResponse().getCookie("custom_refresh");
        Cookie csrf = login.getResponse().getCookie("quimia_csrf");
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("custom_refresh"))
                .andExpect(cookie().doesNotExist("quimia_rt"));
    }
}
