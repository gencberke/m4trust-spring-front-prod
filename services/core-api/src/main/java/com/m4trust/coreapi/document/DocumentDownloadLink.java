package com.m4trust.coreapi.document;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

record DocumentDownloadLink(UUID documentId, String objectVersion,
        URI downloadUrl, Instant expiresAt) {
}
