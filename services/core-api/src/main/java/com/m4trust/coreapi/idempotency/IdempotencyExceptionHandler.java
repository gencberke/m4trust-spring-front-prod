package com.m4trust.coreapi.idempotency;

import java.net.URI;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class IdempotencyExceptionHandler {

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ProblemDetail> handleKeyReused(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The idempotency key was already used for a different request.");
        problem.setType(URI.create(
                "https://problems.m4trust.internal/idempotency-key-reused"));
        problem.setTitle("Idempotency key reused");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "IDEMPOTENCY_KEY_REUSED");
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("correlationId",
                correlationId == null ? "" : correlationId.toString());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
