package com.m4trust.coreapi.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentDispatchRelayProperties.class)
class PaymentConfiguration {
}
