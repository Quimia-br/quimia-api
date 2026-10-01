package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.quimia.domain.account.internal.dto.EmpresaRegisterRequest;
import com.api.quimia.domain.account.internal.usecase.RegistrarEmpresaUseCase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    private RegistrarEmpresaUseCase registrar;

    @Test
    void configuredRefreshCookieNameIsUsedAcrossWebFlow() throws Exception {
        registrar.execute(new EmpresaRegisterRequest("Cookie", "11.222.333/0001-81", "cookie@example.com", "senhaCookie1"));

        MvcResult login = mvc.perform(post("/api/v1/empresas/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cookie@example.com\",\"senha\":\"senhaCookie1\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("custom_refresh"))
                .andExpect(cookie().doesNotExist("quimia_rt"))
                .andReturn();

        Cookie refresh = login.getResponse().getCookie("custom_refresh");
        Cookie csrf = login.getResponse().getCookie("quimia_csrf");
        mvc.perform(post("/api/v1/empresas/auth/refresh")
                        .cookie(refresh, csrf)
                        .header("X-CSRF-Token", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("custom_refresh"))
                .andExpect(cookie().doesNotExist("quimia_rt"));
    }
}
