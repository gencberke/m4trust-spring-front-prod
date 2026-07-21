package com.m4trust.coreapi.integration.payment.moka;

import com.m4trust.coreapi.payment.PaymentProviderPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Explicit local-only selection of the external HTTP emulator adapter. */
@Configuration(proxyBeanMethods = false)
@Profile("local-moka")
@EnableConfigurationProperties(MokaPaymentProviderProperties.class)
class MokaPaymentConfiguration {

    @Bean
    PaymentProviderPort mokaPaymentProviderPort(MokaPaymentProviderProperties properties) {
        return new MokaHttpPaymentProviderAdapter(properties.transportSettings());
    }
}
