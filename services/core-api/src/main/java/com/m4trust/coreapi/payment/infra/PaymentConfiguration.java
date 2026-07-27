package com.m4trust.coreapi.payment.infra;

import com.m4trust.coreapi.payment.domain.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentDispatchRelayProperties.class)
class PaymentConfiguration {}
