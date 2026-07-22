package com.m4trust.coreapi.identity.security;

import java.io.IOException;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.api.ProblemDetailsWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public final class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProblemDetailsAccessDeniedHandler.class);

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
                    ApiErrorCode.CSRF_TOKEN_INVALID);
            return;
        }
        // Catalog conflict: historical ACCESS_DENIED is not in committed ApiErrorCode.
        // Under the current SecurityFilterChain, non-CSRF AccessDeniedException is unexpected.
        // Emit INTERNAL_ERROR/500 instead of inventing or emitting an uncatalogued code.
        LOGGER.error("Unexpected AccessDeniedException without catalog mapping",
                accessDeniedException);
        writer.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error", "Unexpected error",
                "The request could not be completed.",
                ApiErrorCode.INTERNAL_ERROR);
    }
}
