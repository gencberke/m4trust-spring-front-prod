package com.m4trust.coreapi.fulfillment;

class FulfillmentExceptions {

    static class NotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class DealNotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class StartForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class UploadForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class ReviewForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String code;

        Conflict(String code) {
            super("Fulfillment operation conflict: " + code);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    static class DownloadNotAvailable extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
    static class MalformedRequest extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class Validation extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String field;

        Validation(String field) {
            this.field = field;
        }

        String field() {
            return field;
        }
    }
}
