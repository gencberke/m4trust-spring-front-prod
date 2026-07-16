package com.m4trust.coreapi.identity;

import java.io.Serializable;
import java.util.UUID;

public record PublicUser(UUID id, String email, String displayName)
        implements Serializable {
}
