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

    static class StartConflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class UploadConflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class FinalizeConflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class DownloadNotAvailable extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class ReviewConflict extends RuntimeException {
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
