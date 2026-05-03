package com.grabmyseat.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";
    private static final String USER_HEADER = "X-User-Name";
    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final byte[] UNAUTHORIZED_BODY = (
            "{\"status\":401,\"code\":\"unauthorized\",\"message\":\"Authentication is required.\",\"fieldErrors\":{}}")
            .getBytes(StandardCharsets.UTF_8);

    // Only authentication bootstrap and operational health checks are public.  Sale
    // state, queue state and inventory are deliberately authenticated: a browser
    // may call those APIs, but the gateway supplies the caller identity from its
    // signed JWT and never accepts client-provided identity headers.
    private static final List<String> OPEN_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/organizer/register",
            "/api/auth/refresh",
            "/api/auth/otp/**",
            "/api/inventory/status/**",
            "/actuator/health/**",
            "/actuator/info");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final JwtValidator jwt;

    public JwtAuthGatewayFilter(JwtValidator jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isOpen(exchange)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = jwt.parse(header.substring(BEARER.length()));
            if (!jwt.isAccess(claims)) {
                return unauthorized(exchange);
            }
            // overwrite, never trust, any X-User headers the client tried to send
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(USER_HEADER, claims.getSubject())
                    .header(ROLES_HEADER, String.join(",", jwt.roles(claims)))
                    .header(USER_ID_HEADER, String.valueOf(userId(claims)))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException ex) {
            return unauthorized(exchange);
        }
    }

    private boolean isOpen(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        if (OPEN_PATHS.stream().anyMatch(p -> matcher.match(p, path))) {
            return true;
        }
        return exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.GET
                && ("/api/inventory/events".equals(path)
                || matcher.match("/api/inventory/posters/*", path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(UNAUTHORIZED_BODY.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(UNAUTHORIZED_BODY)));
    }

    // run before the routing filters so a bad request never reaches a downstream service
    @Override
    public int getOrder() {
        return -1;
    }

    private Long userId(Claims claims) {
        Object raw = claims.get("userId");
        return raw == null ? null : Long.valueOf(raw.toString());
    }


}
