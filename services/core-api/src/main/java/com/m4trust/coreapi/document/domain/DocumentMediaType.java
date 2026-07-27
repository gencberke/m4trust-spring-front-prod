package com.m4trust.coreapi.document.domain;

public enum DocumentMediaType {
  PDF("application/pdf"),
  DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  private final String value;

  DocumentMediaType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static DocumentMediaType fromValue(String value) {
    for (DocumentMediaType mediaType : values()) {
      if (mediaType.value.equals(value)) {
        return mediaType;
      }
    }
    throw new IllegalArgumentException("unsupported document media type");
  }
}
