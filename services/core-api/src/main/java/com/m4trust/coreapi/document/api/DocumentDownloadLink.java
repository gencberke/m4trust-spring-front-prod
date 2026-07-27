package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

record DocumentDownloadLink(
    UUID documentId, String objectVersion, URI downloadUrl, Instant expiresAt) {}
