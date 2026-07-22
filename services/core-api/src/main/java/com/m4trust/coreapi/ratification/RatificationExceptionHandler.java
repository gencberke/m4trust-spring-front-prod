package com.m4trust.coreapi.ratification;

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

/**
 * Maps ratification service exceptions to the exact Problem Details codes
 * frozen in the OpenAPI contract (paths near line 797, error responses near
 * lines 1690-1745). Non-disclosing 404s never distinguish a missing package
 * from one hidden by Deal visibility.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RatificationExceptionHandler {

    @ExceptionHandler(RatificationMalformedRequestException.class)
    ResponseEntity<ProblemDetail> malformedRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request could not be parsed.", ApiErrorCode.MALFORMED_REQUEST);
    }

    // --- Create (deal-level not-found, forbidden, and conflict class) ---

    @ExceptionHandler(RatificationPackageCreateService.PackageNotFound.class)
    ResponseEntity<ProblemDetail> createDealNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "deal-not-found", "Deal not found",
                "The Deal was not found.", ApiErrorCode.DEAL_NOT_FOUND);
    }

    @ExceptionHandler(RatificationPackageCreateService.Forbidden.class)
    ResponseEntity<ProblemDetail> createForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "ratification-package-create-forbidden",
                "Ratification package create forbidden",
                "Only the Deal's immutable initiator may create a ratification package.",
                ApiErrorCode.RATIFICATION_PACKAGE_CREATE_FORBIDDEN);
    }

    @ExceptionHandler(RatificationPackageCreateService.StateConflict.class)
    ResponseEntity<ProblemDetail> createDealStateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-state-conflict", "Request could not be completed",
                "The Deal is no longer DRAFT.", ApiErrorCode.DEAL_STATE_CONFLICT);
    }

    @ExceptionHandler(RatificationPackageCreateService.StaleDealVersion.class)
    ResponseEntity<ProblemDetail> createStaleDealVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-stale-version", "Resource has changed",
                "The Deal was modified by another operation.", ApiErrorCode.DEAL_STALE_VERSION);
    }

    @ExceptionHandler(RatificationPackageCreateService.NotReady.class)
    ResponseEntity<ProblemDetail> createNotReady(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "ratification-not-ready", "Ratification not ready",
                "Parties, an accepted rule-set, and a current document are required to create a package.",
                ApiErrorCode.RATIFICATION_NOT_READY);
    }

    @ExceptionHandler(RatificationPackageCreateService.InvalidTerms.class)
    ResponseEntity<ProblemDetail> createInvalidTerms(HttpServletRequest request) {
        return validation(request, "commercialTerms");
    }

    // --- Reads (non-disclosing package/deal-visibility 404) ---

    @ExceptionHandler(RatificationPackageReadService.PackageNotFound.class)
    ResponseEntity<ProblemDetail> packageNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "ratification-package-not-found",
                "Ratification package not found", "The ratification package was not found.",
                ApiErrorCode.RATIFICATION_PACKAGE_NOT_FOUND);
    }

    // --- Approve/reject (non-disclosing 404, authority forbidden, conflicts) ---

    @ExceptionHandler(RatificationPackageActionService.NotFound.class)
    ResponseEntity<ProblemDetail> actionNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "ratification-package-not-found",
                "Ratification package not found", "The ratification package was not found.",
                ApiErrorCode.RATIFICATION_PACKAGE_NOT_FOUND);
    }

    @ExceptionHandler(RatificationPackageActionService.Forbidden.class)
    ResponseEntity<ProblemDetail> actionForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "ratification-approval-forbidden",
                "Ratification approval forbidden",
                "Only an ADMIN of the package buyer or seller legal entity may approve or reject.",
                ApiErrorCode.RATIFICATION_APPROVAL_FORBIDDEN);
    }

    @ExceptionHandler(RatificationPackageActionService.Stale.class)
    ResponseEntity<ProblemDetail> actionStale(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "ratification-stale-package", "Resource has changed",
                "The ratification package was modified by another operation.",
                ApiErrorCode.RATIFICATION_STALE_PACKAGE);
    }

    @ExceptionHandler(RatificationPackageActionService.State.class)
    ResponseEntity<ProblemDetail> actionPackageStateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "ratification-package-state-conflict",
                "Ratification package state conflict",
                "The ratification package is no longer PENDING.",
                ApiErrorCode.RATIFICATION_PACKAGE_STATE_CONFLICT);
    }

    @ExceptionHandler(RatificationPackageActionService.DealState.class)
    ResponseEntity<ProblemDetail> actionDealStateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-state-conflict", "Request could not be completed",
                "The Deal is no longer DRAFT.", ApiErrorCode.DEAL_STATE_CONFLICT);
    }

    private ResponseEntity<ProblemDetail> validation(HttpServletRequest request, String field) {
        ProblemDetail problem = response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-failed", "Validation failed",
                "One or more fields are invalid.", ApiErrorCode.VALIDATION_FAILED).getBody();
        problem.setProperty("errors", List.of(new FieldValidationError(field, FieldErrorCode.INVALID,
                "The value is invalid.")));
        return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
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
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
