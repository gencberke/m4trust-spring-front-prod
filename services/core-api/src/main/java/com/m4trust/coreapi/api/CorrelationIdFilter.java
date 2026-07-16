package com.m4trust.coreapi.api;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the incoming {@code X-Correlation-ID} request header. If the value is
 * absent or not a valid UUID, a new UUID is generated. The resolved value is
 * stored as a request attribute ({@link #ATTRIBUTE}) and in the SLF4J MDC
 * (key {@link #MDC_KEY}) for log enrichment, and always written back as the
 * {@code X-Correlation-ID} response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String ATTRIBUTE = "correlationId";
    static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(String header) {
        if (header != null && !header.isBlank()) {
            try {
                UUID.fromString(header.trim());
                return header.trim();
            } catch (IllegalArgumentException ignored) {
                // not a valid UUID — fall through to generate one
            }
        }
        return UUID.randomUUID().toString();
    }
}
