package com.m4trust.coreapi.identity.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class AbsoluteSessionTimeoutFilterTest {

    @Test
    void invalidatesAnActiveSessionAtTheServerSideAbsoluteDeadline()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        Instant deadline = Instant.ofEpochMilli(session.getCreationTime())
                .plus(Duration.ofHours(8));
        AbsoluteSessionTimeoutFilter filter = new AbsoluteSessionTimeoutFilter(
                Clock.fixed(deadline, ZoneOffset.UTC),
                new SessionSecurityProperties(Duration.ofHours(8)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> continued.set(true));

        assertTrue(continued.get());
        assertThrows(IllegalStateException.class, session::getCreationTime);
    }
}
