package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;

final class DocumentExceptions {
  private DocumentExceptions() {}

  static final class MalformedRequest extends RuntimeException {}

  static final class DealNotFound extends RuntimeException {}

  static final class NotFound extends RuntimeException {}

  static final class MutationForbidden extends RuntimeException {}

  static final class UploadNotAllowed extends RuntimeException {}

  static final class UploadExpired extends RuntimeException {}

  static final class UploadStateConflict extends RuntimeException {}

  static final class VerificationFailed extends RuntimeException {}

  static final class DownloadNotAvailable extends RuntimeException {}

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
