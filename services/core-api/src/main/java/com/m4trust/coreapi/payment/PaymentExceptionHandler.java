package com.m4trust.coreapi.payment;

import java.net.URI;

import com.m4trust.coreapi.api.ApiErrorCode;
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

/** Maps payment service exceptions to the exact Problem Details codes frozen in the OpenAPI contract. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class PaymentExceptionHandler {

    @ExceptionHandler(PaymentMalformedRequestException.class)
    ResponseEntity<ProblemDetail> malformedRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request could not be parsed.", ApiErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(PaymentExceptions.DealNotFound.class)
    ResponseEntity<ProblemDetail> dealNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "deal-not-found", "Deal not found",
                "The Deal was not found.", ApiErrorCode.DEAL_NOT_FOUND);
    }

    @ExceptionHandler(PaymentExceptions.FundingPlanNotFound.class)
    ResponseEntity<ProblemDetail> fundingPlanNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "funding-plan-not-found", "Funding plan not found",
                "The funding plan was not found.", ApiErrorCode.FUNDING_PLAN_NOT_FOUND);
    }

    @ExceptionHandler(PaymentExceptions.FundingUnitNotFound.class)
    ResponseEntity<ProblemDetail> fundingUnitNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "funding-unit-not-found", "Funding unit not found",
                "The funding unit was not found.", ApiErrorCode.FUNDING_UNIT_NOT_FOUND);
    }

    @ExceptionHandler(PaymentExceptions.PaymentOperationNotFound.class)
    ResponseEntity<ProblemDetail> paymentOperationNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "payment-operation-not-found", "Payment operation not found",
                "The payment operation was not found.", ApiErrorCode.PAYMENT_OPERATION_NOT_FOUND);
    }

    @ExceptionHandler(PaymentExceptions.FundingMutationForbidden.class)
    ResponseEntity<ProblemDetail> fundingMutationForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "funding-mutation-forbidden", "Funding mutation forbidden",
                "Only an ADMIN of the Deal's buyer legal entity may perform this funding action.",
                ApiErrorCode.FUNDING_MUTATION_FORBIDDEN);
    }

    @ExceptionHandler(PaymentExceptions.DealStateConflict.class)
    ResponseEntity<ProblemDetail> dealStateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-state-conflict", "Request could not be completed",
                "The Deal is not ACTIVE.", ApiErrorCode.DEAL_STATE_CONFLICT);
    }

    @ExceptionHandler(PaymentExceptions.DealStaleVersion.class)
    ResponseEntity<ProblemDetail> dealStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-stale-version", "Resource has changed",
                "The Deal was modified by another operation.", ApiErrorCode.DEAL_STALE_VERSION);
    }

    @ExceptionHandler(PaymentExceptions.FundingPlanAlreadyExists.class)
    ResponseEntity<ProblemDetail> fundingPlanAlreadyExists(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "funding-plan-already-exists", "Funding plan already exists",
                "A funding plan already exists for this Deal.", ApiErrorCode.FUNDING_PLAN_ALREADY_EXISTS);
    }

    @ExceptionHandler(PaymentExceptions.FundingUnitStaleVersion.class)
    ResponseEntity<ProblemDetail> fundingUnitStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "funding-unit-stale-version", "Resource has changed",
                "The funding unit was modified by another operation.", ApiErrorCode.FUNDING_UNIT_STALE_VERSION);
    }

    @ExceptionHandler(PaymentExceptions.FundingUnitAlreadyFunded.class)
    ResponseEntity<ProblemDetail> fundingUnitAlreadyFunded(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "funding-unit-already-funded", "Funding unit already funded",
                "The funding unit is already FUNDED.", ApiErrorCode.FUNDING_UNIT_ALREADY_FUNDED);
    }

    @ExceptionHandler(PaymentExceptions.PaymentOperationInFlight.class)
    ResponseEntity<ProblemDetail> paymentOperationInFlight(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "payment-operation-in-flight", "Payment operation in flight",
                "A CREATED or UNCONFIRMED payment operation is already in flight for this funding unit.",
                ApiErrorCode.PAYMENT_OPERATION_IN_FLIGHT);
    }

    @ExceptionHandler(PaymentExceptions.PaymentOperationStaleVersion.class)
    ResponseEntity<ProblemDetail> paymentOperationStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "payment-operation-stale-version", "Resource has changed",
                "The payment operation was modified by another operation.",
                ApiErrorCode.PAYMENT_OPERATION_STALE_VERSION);
    }

    @ExceptionHandler(PaymentExceptions.PaymentOperationStateConflict.class)
    ResponseEntity<ProblemDetail> paymentOperationStateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "payment-operation-state-conflict",
                "Payment operation state conflict",
                "The payment operation already reached a terminal result and cannot be reconciled.",
                ApiErrorCode.PAYMENT_OPERATION_STATE_CONFLICT);
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
