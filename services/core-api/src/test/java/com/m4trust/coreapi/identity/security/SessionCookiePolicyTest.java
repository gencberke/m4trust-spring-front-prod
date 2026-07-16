package com.m4trust.coreapi.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class SessionCookiePolicyTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void productionDefaultsAreHostPrefixedAndSecure() throws IOException {
        PropertySourcesPropertyResolver resolver = resolver(false);

        assertEquals("__Host-M4TRUST_SESSION", resolver.getProperty(
                "server.servlet.session.cookie.name"));
        assertEquals("true", resolver.getProperty(
                "server.servlet.session.cookie.secure"));
        assertEquals("true", resolver.getProperty(
                "server.servlet.session.cookie.http-only"));
        assertEquals("lax", resolver.getProperty(
                "server.servlet.session.cookie.same-site"));
        assertEquals("/", resolver.getProperty(
                "server.servlet.session.cookie.path"));
    }

    @Test
    void localProfileChangesOnlyNameAndSecureTransport() throws IOException {
        PropertySourcesPropertyResolver resolver = resolver(true);

        assertEquals("M4TRUST_SESSION", resolver.getProperty(
                "server.servlet.session.cookie.name"));
        assertEquals("false", resolver.getProperty(
                "server.servlet.session.cookie.secure"));
        assertEquals("true", resolver.getProperty(
                "server.servlet.session.cookie.http-only"));
        assertEquals("lax", resolver.getProperty(
                "server.servlet.session.cookie.same-site"));
        assertEquals("/", resolver.getProperty(
                "server.servlet.session.cookie.path"));
    }

    private PropertySourcesPropertyResolver resolver(boolean local) throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        loader.load("base", new ClassPathResource("application.yml"))
                .forEach(propertySources::addLast);
        if (local) {
            loader.load("local", new ClassPathResource("application-local.yml"))
                    .forEach(propertySources::addFirst);
        }
        return new PropertySourcesPropertyResolver(propertySources);
    }
}
