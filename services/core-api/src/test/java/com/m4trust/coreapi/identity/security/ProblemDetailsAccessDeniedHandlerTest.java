package com.m4trust.coreapi.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.api.ProblemDetailsWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class ProblemDetailsAccessDeniedHandlerTest {

    private final ProblemDetailsWriter writer = mock(ProblemDetailsWriter.class);
    private final ProblemDetailsAccessDeniedHandler handler =
            new ProblemDetailsAccessDeniedHandler(writer);

    @Test
    void csrfDenialUsesCsrfTokenInvalid() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        handler.handle(request, response, new MissingCsrfTokenException(null));

        ArgumentCaptor<ApiErrorCode> code = ArgumentCaptor.forClass(ApiErrorCode.class);
        verify(writer).write(eq(request), eq(response), eq(HttpStatus.FORBIDDEN),
                eq("csrf-token-invalid"), eq("CSRF validation failed"),
                eq("The CSRF token is missing or invalid."), code.capture());
        assertEquals(ApiErrorCode.CSRF_TOKEN_INVALID, code.getValue());
    }

    @Test
    void genericDenialUsesGrandfatheredAccessDenied() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        handler.handle(request, response, new AccessDeniedException("denied"));

        ArgumentCaptor<ApiErrorCode> code = ArgumentCaptor.forClass(ApiErrorCode.class);
        verify(writer).write(eq(request), eq(response), eq(HttpStatus.FORBIDDEN),
                eq("access-denied"), eq("Access denied"),
                eq("The authenticated user cannot perform this operation."), code.capture());
        assertEquals(ApiErrorCode.ACCESS_DENIED, code.getValue());
    }
}
