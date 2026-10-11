package com.api.quimia.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CookieCsrfFilter extends OncePerRequestFilter {
    /**
     * Casa pelo caminho decodificado, como o MVC e o Spring Security: variantes como
     * `/empresas/auth/%72efresh` chegam ao mesmo controller e não podem escapar da checagem.
     */
    private static final RequestMatcher PROTECTED_WEB_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/empresas/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/empresas/auth/logout"));
    private final boolean enabled;
    private final String cookieName;

    public CookieCsrfFilter(
            @Value("${app.csrf.enabled:true}") boolean enabled,
            @Value("${app.auth.cookie-name:quimia_rt}") String cookieName) {
        this.enabled = enabled;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !PROTECTED_WEB_ENDPOINTS.matches(request) || !hasRefreshCookie(request)) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("X-CSRF-Token");
        String cookie = csrfCookie(request);
        if (header == null || cookie == null || !constantTime(header, cookie)) {
            problem(response, 403, "csrf_required");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private String csrfCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if ("quimia_csrf".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean constantTime(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private static void problem(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"title\":\""
                + (status == 403 ? "Forbidden" : "Error") + "\",\"status\":" + status
                + ",\"detail\":\"" + code + "\"}");
    }
}
