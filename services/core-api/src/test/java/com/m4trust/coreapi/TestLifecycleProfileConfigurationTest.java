package com.m4trust.coreapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * Proves the test-only lifecycle profile disables background work and idle pool retention without
 * weakening base or local runtime defaults.
 */
class TestLifecycleProfileConfigurationTest {

  private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

  @Test
  void testProfileDisablesBackgroundCapabilitiesAndSessionCleanup() throws IOException {
    PropertySourcesPropertyResolver test =
        resolver("application-local.yml", "application-test.yml");

    assertEquals("false", test.getProperty("app.messaging.topology.enabled"));
    assertEquals("false", test.getProperty("app.messaging.relay.enabled"));
    assertEquals("false", test.getProperty("app.payment.dispatch.relay.enabled"));
    assertEquals("-", test.getProperty("spring.session.jdbc.cleanup-cron"));
    assertEquals("0", test.getProperty("spring.datasource.hikari.minimum-idle"));
    assertEquals("10000", test.getProperty("spring.datasource.hikari.idle-timeout"));
    assertEquals("0", test.getProperty("spring.datasource.hikari.keepalive-time"));
  }

  @Test
  void localAndBaseDefaultsRemainUnchangedByTestProfileFile() throws IOException {
    PropertySourcesPropertyResolver base = resolver();
    PropertySourcesPropertyResolver local = resolver("application-local.yml");

    assertEquals("false", base.getProperty("app.messaging.topology.enabled"));
    assertEquals("false", base.getProperty("app.messaging.relay.enabled"));
    assertEquals("false", base.getProperty("app.payment.dispatch.relay.enabled"));
    assertEquals("true", local.getProperty("app.messaging.topology.enabled"));
    assertEquals("true", local.getProperty("app.messaging.relay.enabled"));
    assertNotEquals("-", local.getProperty("spring.session.jdbc.cleanup-cron", ""));
  }

  private PropertySourcesPropertyResolver resolver(String... profiles) throws IOException {
    MutablePropertySources propertySources = sources();
    for (String profile : profiles) {
      loader.load(profile, new ClassPathResource(profile)).forEach(propertySources::addFirst);
    }
    return new PropertySourcesPropertyResolver(propertySources);
  }

  private MutablePropertySources sources() throws IOException {
    MutablePropertySources propertySources = new MutablePropertySources();
    loader.load("base", new ClassPathResource("application.yml")).forEach(propertySources::addLast);
    return propertySources;
  }
}
