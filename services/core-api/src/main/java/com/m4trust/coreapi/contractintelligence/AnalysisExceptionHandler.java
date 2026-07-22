package com.m4trust.coreapi.contractintelligence;

import java.net.URI;
import java.util.List;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.api.FieldErrorCode;
import com.m4trust.coreapi.api.FieldValidationError;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class AnalysisExceptionHandler {

    @ExceptionHandler(AnalysisExceptions.MalformedRequest.class)
    ResponseEntity<ProblemDetail> malformedRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request could not be parsed.", ApiErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(AnalysisExceptions.DealNotFound.class)
    ResponseEntity<ProblemDetail> dealNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "deal-not-found", "Deal not found",
                "The Deal was not found.", ApiErrorCode.DEAL_NOT_FOUND);
    }

    @ExceptionHandler(AnalysisExceptions.RequestForbidden.class)
    ResponseEntity<ProblemDetail> requestForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "deal-analysis-request-forbidden",
                "Deal analysis request forbidden",
                "The active legal entity cannot request analysis for this Deal.",
                ApiErrorCode.DEAL_ANALYSIS_REQUEST_FORBIDDEN);
    }

    @ExceptionHandler(AnalysisExceptions.Conflict.class)
    ResponseEntity<ProblemDetail> conflict(AnalysisExceptions.Conflict exception,
            HttpServletRequest request) {
        ApiErrorCode code = ApiErrorCode.valueOf(exception.code());
        return response(request, HttpStatus.CONFLICT,
                code.name().toLowerCase().replace('_', '-'),
                "Analysis request conflict",
                "The request conflicts with the current Deal analysis state.",
                code);
    }

    @ExceptionHandler(AnalysisExceptions.ReviewAcceptanceForbidden.class)
    ResponseEntity<ProblemDetail> reviewAcceptanceForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "deal-review-acceptance-forbidden",
                "Deal review acceptance forbidden",
                "The active legal entity cannot accept review for this Deal.",
                ApiErrorCode.DEAL_REVIEW_ACCEPTANCE_FORBIDDEN);
    }

    @ExceptionHandler(AnalysisExceptions.RuleSetVersionNotFound.class)
    ResponseEntity<ProblemDetail> ruleSetVersionNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "rule-set-version-not-found",
                "Rule-set version not found", "The RuleSetVersion was not found.",
                ApiErrorCode.RULE_SET_VERSION_NOT_FOUND);
    }

    @ExceptionHandler(AnalysisExceptions.Validation.class)
    ResponseEntity<ProblemDetail> validation(AnalysisExceptions.Validation exception,
            HttpServletRequest request) {
        ProblemDetail problem = response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                "review-validation-failed", "Review validation failed",
                "A reviewed rule value is invalid.", ApiErrorCode.VALIDATION_FAILED).getBody();
        problem.setProperty("errors", List.of(new FieldValidationError(exception.field(),
                FieldErrorCode.INVALID, "The value is invalid.")));
        return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request,
            HttpStatus status, String typeSlug, String title, String detail, ApiErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://problems.m4trust.internal/" + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("correlationId", correlationId == null ? "" : correlationId.toString());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
