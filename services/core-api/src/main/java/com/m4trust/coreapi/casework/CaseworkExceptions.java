package com.m4trust.coreapi.casework;

import java.util.List;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.api.FieldErrorCode;
import com.m4trust.coreapi.api.FieldValidationError;

class CaseworkExceptions {

    static class MalformedRequest extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class NotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class DisputeNotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class OpenForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class CommentForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class AcknowledgeForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class WithdrawForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final ApiErrorCode code;

        Conflict(ApiErrorCode code) {
            super("Casework operation conflict: " + code.name());
            this.code = code;
        }

        ApiErrorCode code() {
            return code;
        }
    }

    static class Validation extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final List<FieldValidationError> errors;

        Validation(String field, FieldErrorCode code, String message) {
            errors = List.of(new FieldValidationError(field, code, message));
        }

        List<FieldValidationError> errors() {
            return errors;
        }
    }
}
