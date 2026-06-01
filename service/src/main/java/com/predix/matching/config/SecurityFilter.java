package com.predix.matching.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Placeholder auth: trusts BFF user id header. Signature validation reserved for production.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "bffUserId";

    private final PredixProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (properties.getSecurity().isTrustBffUserHeader()) {
            String userId = request.getHeader(properties.getSecurity().getBffUserIdHeader());
            if (userId != null && !userId.isBlank()) {
                request.setAttribute(ATTR_USER_ID, userId);
            }
        }
        chain.doFilter(request, response);
    }
}
