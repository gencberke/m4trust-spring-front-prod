package com.m4trust.coreapi.organization;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class OrganizationExceptionHandler {

    @ExceptionHandler(MalformedLegalEntityIdException.class)
    ResponseEntity<ProblemDetail> handleMalformedLegalEntityId(
            HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST,
                "malformed-request", "Malformed request",
                "The legal entity identifier could not be parsed.",
                "MALFORMED_REQUEST");
    }

    @ExceptionHandler(LegalEntityAccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(
            HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "legal-entity-access-denied", "Legal entity access denied",
                "The required legal entity context is invalid.",
                "LEGAL_ENTITY_ACCESS_DENIED");
    }

    @ExceptionHandler(LegalEntityNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "legal-entity-not-found", "Legal entity not found",
                "The legal entity was not found.",
                "LEGAL_ENTITY_NOT_FOUND");
    }

    private ResponseEntity<ProblemDetail> response(
            HttpServletRequest request,
            HttpStatus status,
            String typeSlug,
            String title,
            String detail,
            String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, detail);
        problem.setType(URI.create(
                "https://problems.m4trust.internal/" + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        Object correlationId = request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("correlationId",
                correlationId == null ? "" : correlationId.toString());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
