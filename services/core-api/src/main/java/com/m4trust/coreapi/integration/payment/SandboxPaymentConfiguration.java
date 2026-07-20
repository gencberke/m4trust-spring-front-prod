package com.m4trust.coreapi.integration.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-sandbox")
@EnableConfigurationProperties(SandboxPaymentProviderProperties.class)
class SandboxPaymentConfiguration {
}
