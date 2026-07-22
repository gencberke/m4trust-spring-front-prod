package com.m4trust.coreapi.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PackagedSchemaLoadTest {

    @Test
    void everyClasspathSchemaJsonLoadsAndParses() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:contracts/schemas/**/*.json");
        assertTrue(resources.length > 0, "expected packaged contracts/schemas/**/*.json");

        ObjectMapper mapper = new ObjectMapper();
        List<String> loaded = new ArrayList<>();
        for (Resource resource : resources) {
            assertTrue(resource.exists());
            try (InputStream input = resource.getInputStream()) {
                JsonNode node = mapper.readTree(input);
                assertNotNull(node);
                assertFalse(node.isMissingNode());
            }
            loaded.add(ContractBundleDigest.bundleRelativePath(resource));
        }
        assertFalse(loaded.isEmpty());
    }
}
