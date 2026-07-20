package com.m4trust.coreapi.fulfillment;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceMediaType {
    APPLICATION_PDF("application/pdf"),
    APPLICATION_DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    VIDEO_MP4("video/mp4");

    private final String value;

    EvidenceMediaType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static EvidenceMediaType fromValue(String value) {
        for (EvidenceMediaType mediaType : values()) {
            if (mediaType.value.equals(value)) {
                return mediaType;
            }
        }
        throw new IllegalArgumentException("Unknown evidence media type: " + value);
    }
}
