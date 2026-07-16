package com.m4trust.coreapi.organization;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class OrganizationWebConfiguration implements WebMvcConfigurer {

    private final OperationContextArgumentResolver argumentResolver;

    OrganizationWebConfiguration(
            OperationContextArgumentResolver argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(argumentResolver);
    }
}
