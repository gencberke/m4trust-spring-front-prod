package com.m4trust.coreapi.identity.security;

import java.io.IOException;

import com.m4trust.coreapi.api.ProblemDetailsWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public final class ProblemDetailsAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private final ProblemDetailsWriter writer;

    public ProblemDetailsAuthenticationEntryPoint(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED,
                "auth-session-expired", "Authentication required",
                "An active authenticated session is required.",
                "AUTH_SESSION_EXPIRED");
    }
}
