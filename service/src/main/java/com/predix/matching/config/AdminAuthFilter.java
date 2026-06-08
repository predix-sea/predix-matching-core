package com.predix.matching.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "predix.admin.enabled", havingValue = "true")
public class AdminAuthFilter extends OncePerRequestFilter {

    private final PredixProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    jakarta.servlet.FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {
        String configuredKey = properties.getAdmin().getApiKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            reject(response, "Admin API key is not configured");
            return;
        }

        String providedKey = request.getHeader("X-Admin-Api-Key");
        if (!configuredKey.equals(providedKey)) {
            reject(response, "Invalid admin API key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
