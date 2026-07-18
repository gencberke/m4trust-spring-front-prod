package com.m4trust.coreapi.document;

enum DocumentMediaType {
    PDF("application/pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String value;

    DocumentMediaType(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }

    static DocumentMediaType fromValue(String value) {
        for (DocumentMediaType mediaType : values()) {
            if (mediaType.value.equals(value)) {
                return mediaType;
            }
        }
        throw new IllegalArgumentException("unsupported document media type");
    }
}
