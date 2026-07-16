package com.m4trust.coreapi.identity.security;

import java.io.IOException;

import com.m4trust.coreapi.api.ProblemDetailsWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public final class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailsWriter writer;

    public ProblemDetailsAccessDeniedHandler(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException) {
            writer.write(request, response, HttpStatus.FORBIDDEN,
                    "csrf-token-invalid", "CSRF validation failed",
                    "The CSRF token is missing or invalid.",
                    "CSRF_TOKEN_INVALID");
            return;
        }
        writer.write(request, response, HttpStatus.FORBIDDEN,
                "access-denied", "Access denied",
                "The authenticated user cannot perform this operation.",
                "ACCESS_DENIED");
    }
}
