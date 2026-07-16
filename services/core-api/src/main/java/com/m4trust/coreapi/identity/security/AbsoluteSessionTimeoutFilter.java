package com.m4trust.coreapi.identity.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.filter.OncePerRequestFilter;

public final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final SessionSecurityProperties properties;

    public AbsoluteSessionTimeoutFilter(
            Clock clock, SessionSecurityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && isAbsolutelyExpired(session)) {
            session.invalidate();
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAbsolutelyExpired(HttpSession session) {
        Instant deadline = Instant.ofEpochMilli(session.getCreationTime())
                .plus(properties.absoluteTimeout());
        return !clock.instant().isBefore(deadline);
    }
}
