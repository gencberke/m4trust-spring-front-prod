package com.m4trust.coreapi.contractintelligence;

import com.m4trust.coreapi.api.ApiErrorCode;

final class AnalysisExceptions {

    private AnalysisExceptions() {
    }

    static final class DealNotFound extends RuntimeException {
    }

    static final class MalformedRequest extends RuntimeException {
    }

    static final class RequestForbidden extends RuntimeException {
    }

    static final class ReviewAcceptanceForbidden extends RuntimeException {
    }

    static final class RuleSetVersionNotFound extends RuntimeException {
    }

    static final class Conflict extends RuntimeException {
        private final ApiErrorCode code;

        Conflict(ApiErrorCode code) {
            this.code = code;
        }

        ApiErrorCode code() {
            return code;
        }
    }

    static final class Validation extends RuntimeException {
        private final String field;

        Validation(String field) {
            this.field = field;
        }

        String field() {
            return field;
        }
    }
}
