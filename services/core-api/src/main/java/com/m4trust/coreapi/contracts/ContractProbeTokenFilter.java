package com.m4trust.coreapi.contracts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces Authorization Bearer probe tokens for {@code /internal/**}.
 * Never logs token values.
 */
public final class ContractProbeTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ContractProbeTokenProperties properties;

    public ContractProbeTokenFilter(ContractProbeTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path == null || !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (presented.isEmpty() || !matchesConfiguredToken(presented)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesConfiguredToken(String presented) {
        return constantTimeEquals(presented, properties.probeToken())
                || (!properties.probeTokenPrevious().isEmpty()
                        && constantTimeEquals(presented, properties.probeTokenPrevious()));
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || right.isEmpty()) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
