package com.m4trust.coreapi.contracts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ContractProbeTokenProperties.class,
        ReleaseIdentityProperties.class
})
public class ContractsConfiguration {

    @Bean
    ContractProbeTokenFilter contractProbeTokenFilter(ContractProbeTokenProperties properties) {
        return new ContractProbeTokenFilter(properties);
    }
}
