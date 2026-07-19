package com.m4trust.coreapi.contractintelligence;

final class AnalysisExceptions {

    private AnalysisExceptions() {
    }

    static final class DealNotFound extends RuntimeException {
    }

    static final class MalformedRequest extends RuntimeException {
    }

    static final class RequestForbidden extends RuntimeException {
    }

    static final class Conflict extends RuntimeException {
        private final String code;

        Conflict(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
