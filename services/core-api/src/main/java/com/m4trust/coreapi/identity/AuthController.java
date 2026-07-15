package com.m4trust.coreapi.identity;

import java.net.URI;
import java.util.List;

import com.m4trust.coreapi.identity.security.AuthenticatedSessionManager;
import com.m4trust.coreapi.identity.security.IdentityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthenticatedSessionManager sessionManager;
    private final IdentityService identityService;
    private final SecurityContextLogoutHandler logoutHandler =
            new SecurityContextLogoutHandler();

    public AuthController(AuthenticationManager authenticationManager,
            AuthenticatedSessionManager sessionManager,
            IdentityService identityService) {
        this.authenticationManager = authenticationManager;
        this.sessionManager = sessionManager;
        this.identityService = identityService;
    }

    @PostMapping("/register")
    public ResponseEntity<PublicUser> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        PublicUser user = identityService.register(
                request.email(), request.password(), request.displayName());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                IdentityPrincipal.from(user), null, List.of());
        sessionManager.establish(authentication, httpRequest, httpResponse);
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(user);
    }

    @PostMapping("/login")
    public PublicUser login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email(), request.password()));
            sessionManager.establish(authentication, httpRequest, httpResponse);
            return ((IdentityPrincipal) authentication.getPrincipal()).toPublicUser();
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public PublicUser me(@AuthenticationPrincipal IdentityPrincipal principal) {
        return principal.toPublicUser();
    }
}
