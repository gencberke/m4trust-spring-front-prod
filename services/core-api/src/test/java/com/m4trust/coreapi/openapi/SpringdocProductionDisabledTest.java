package com.m4trust.coreapi.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

class SpringdocProductionDisabledTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void defaultApplicationConfigDisablesApiDocsAndSwaggerUi() throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        loader.load("base", new ClassPathResource("application.yml"))
                .forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver =
                new PropertySourcesPropertyResolver(propertySources);

        assertEquals("false", resolver.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", resolver.getProperty("springdoc.swagger-ui.enabled"));
        // Contract profile may enable docs for tests only; production base stays off.
        assertNull(resolver.getProperty("springdoc.api-docs.path"));
    }
}
