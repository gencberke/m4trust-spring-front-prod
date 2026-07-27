package com.m4trust.coreapi.payment.infra;

import com.m4trust.coreapi.payment.domain.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payment.dispatch.relay")
record PaymentDispatchRelayProperties(Duration fixedDelay, Duration claimTimeout, int batchSize) {}
