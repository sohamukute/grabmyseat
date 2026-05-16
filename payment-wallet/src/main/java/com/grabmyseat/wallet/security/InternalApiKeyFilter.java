package com.grabmyseat.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String internalApiKey;

    public InternalApiKeyFilter(@Value("${wallet.internal.api-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/wallet/internal")) {
            String provided = request.getHeader("X-Internal-Api-Key");
            if (provided == null || !provided.equals(internalApiKey)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid internal api key");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
