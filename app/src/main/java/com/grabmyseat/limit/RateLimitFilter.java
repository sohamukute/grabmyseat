package com.grabmyseat.limit;

import com.grabmyseat.auth.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY = "{\"status\":429,\"message\":\"Too many tries. Wait a minute and try again.\",\"fieldErrors\":{}}";

    private final RateLimit limit;

    public RateLimitFilter(RateLimit limit) {
        this.limit = limit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        switch (request.getRequestURI()) {
            case "/api/bookings", "/api/queue" -> {
                Long user = userId();
                if (user != null && limit.hit("book:" + user, 60) > 10) {
                    refuse(response, 60);
                    return;
                }
                chain.doFilter(request, response);
            }
            case "/api/auth/login" -> {
                String key = "login:" + String.valueOf(request.getParameter("username")).toLowerCase();
                if (limit.count(key, 900) >= 10) {
                    refuse(response, 900);
                    return;
                }
                chain.doFilter(request, response);
                if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                    limit.hit(key, 900);
                }
            }
            case "/api/auth/verify/resend" -> {
                Long user = userId();
                if (user != null && limit.hit("resend:" + user, 3600) > 3) {
                    refuse(response, 3600);
                    return;
                }
                chain.doFilter(request, response);
            }
            case "/api/auth/forgot" -> {
                if (limit.hit("forgot:" + request.getRemoteAddr(), 900) > 5) {
                    refuse(response, 900);
                    return;
                }
                chain.doFilter(request, response);
            }
            case "/api/auth/register" -> {
                if (limit.hit("register:" + request.getRemoteAddr(), 600) > 30) {
                    refuse(response, 600);
                    return;
                }
                chain.doFilter(request, response);
            }
            default -> chain.doFilter(request, response);
        }
    }

    private Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CurrentUser user ? user.getId() : null;
    }

    private void refuse(HttpServletResponse response, int seconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(seconds));
        response.setContentType("application/json");
        response.getWriter().write(TOO_MANY);
    }
}
