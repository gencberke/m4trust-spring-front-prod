package com.m4trust.coreapi.casework;

import java.util.List;

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

    static class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String code;

        Conflict(String code) {
            super("Casework operation conflict: " + code);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    static class Validation extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final List<FieldValidationError> errors;

        Validation(String field, String code, String message) {
            errors = List.of(new FieldValidationError(field, code, message));
        }

        List<FieldValidationError> errors() {
            return errors;
        }
    }
}
