package com.m4trust.coreapi.fulfillment;

import com.m4trust.coreapi.api.ApiErrorCode;

class FulfillmentExceptions {

    static class FulfillmentNotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class EvidenceNotFound extends RuntimeException {
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

    static class RequestForbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final ApiErrorCode code;

        Conflict(ApiErrorCode code) {
            super("Fulfillment operation conflict: " + code.name());
            this.code = code;
        }

        Conflict(String code) {
            this(ApiErrorCode.valueOf(code));
        }

        ApiErrorCode code() {
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
