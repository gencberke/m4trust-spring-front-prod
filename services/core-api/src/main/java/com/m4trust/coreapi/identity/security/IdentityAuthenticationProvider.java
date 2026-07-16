package com.m4trust.coreapi.identity.security;

import java.util.List;

import com.m4trust.coreapi.identity.IdentityService;
import com.m4trust.coreapi.identity.InvalidCredentialsException;
import com.m4trust.coreapi.identity.PublicUser;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public final class IdentityAuthenticationProvider implements AuthenticationProvider {

    private final IdentityService identityService;

    public IdentityAuthenticationProvider(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        try {
            PublicUser user = identityService.authenticate(
                    authentication.getName(), authentication.getCredentials().toString());
            return UsernamePasswordAuthenticationToken.authenticated(
                    IdentityPrincipal.from(user), null, List.of());
        } catch (InvalidCredentialsException exception) {
            throw new BadCredentialsException("Invalid credentials.");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
