package com.m4trust.coreapi.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class DeploymentConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void deployedRuntimeDisablesFlywayAndStagingTrustsForwardedHeaders() throws IOException {
        PropertySourcesPropertyResolver base = resolver();
        PropertySourcesPropertyResolver staging = resolver("application-staging.yml");
        PropertySourcesPropertyResolver local = resolver("application-local.yml");

        assertEquals("false", base.getProperty("spring.flyway.enabled"));
        assertEquals("false", staging.getProperty("spring.flyway.enabled"));
        assertEquals("framework", staging.getProperty("server.forward-headers-strategy"));
        assertEquals("true", local.getProperty("spring.flyway.enabled"));
        assertEquals("none", local.getProperty("server.forward-headers-strategy"));
    }

    @Test
    void genericReleaseIdentityFeedsInfoAndStructuredLogMetadata() throws IOException {
        MutablePropertySources propertySources = sources();
        propertySources.addFirst(new MapPropertySource("release", Map.of(
                "APP_VERSION", "7.0.0-rc.1",
                "GIT_COMMIT_SHA", "0123456789abcdef",
                "APP_ENVIRONMENT", "staging",
                "BUILD_TIME", "2026-07-17T12:00:00Z")));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(
                propertySources);

        assertEquals("7.0.0-rc.1", resolver.getProperty("info.app.version"));
        assertEquals("0123456789abcdef", resolver.getProperty("info.app.gitCommitSha"));
        assertEquals("staging", resolver.getProperty("info.app.environment"));
        assertEquals("2026-07-17T12:00:00Z", resolver.getProperty("info.app.buildTime"));
        assertEquals("true", resolver.getProperty("management.info.env.enabled"));
        assertEquals("7.0.0-rc.1", resolver.getProperty(
                "logging.structured.json.add.version"));
        assertEquals("0123456789abcdef", resolver.getProperty(
                "logging.structured.json.add.gitCommitSha"));
        assertEquals("staging", resolver.getProperty(
                "logging.structured.json.add.environment"));
        assertEquals("2026-07-17T12:00:00Z", resolver.getProperty(
                "logging.structured.json.add.buildTime"));
    }

    private PropertySourcesPropertyResolver resolver(String... profiles) throws IOException {
        MutablePropertySources propertySources = sources();
        for (String profile : profiles) {
            loader.load(profile, new ClassPathResource(profile))
                    .forEach(propertySources::addFirst);
        }
        return new PropertySourcesPropertyResolver(propertySources);
    }

    private MutablePropertySources sources() throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        loader.load("base", new ClassPathResource("application.yml"))
                .forEach(propertySources::addLast);
        return propertySources;
    }
}
