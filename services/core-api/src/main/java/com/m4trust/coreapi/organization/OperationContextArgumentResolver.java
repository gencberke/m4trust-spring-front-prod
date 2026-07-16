package com.m4trust.coreapi.organization;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

@Component
class OperationContextArgumentResolver
        implements HandlerMethodArgumentResolver {

    static final String LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";

    private final OperationContextService contextService;

    OperationContextArgumentResolver(OperationContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == OperationContext.class
                && parameter.hasParameterAnnotation(
                        ResolvedOperationContext.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(
                HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("Servlet request is unavailable");
        }
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "Operation context requires authentication");
        }

        ResolvedOperationContext annotation =
                parameter.getParameterAnnotation(
                        ResolvedOperationContext.class);
        if (annotation == null) {
            throw new IllegalStateException(
                    "Resolved operation annotation is unavailable");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>)
                request.getAttribute(
                        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        boolean pathMatchRequired =
                !annotation.legalEntityPathVariable().isBlank();
        String requestedLegalEntityId =
                !pathMatchRequired || pathVariables == null
                        ? null
                        : pathVariables.get(
                                annotation.legalEntityPathVariable());
        return contextService.resolve(
                authentication.getName(),
                requestedLegalEntityId,
                request.getHeader(LEGAL_ENTITY_HEADER),
                annotation.value(),
                pathMatchRequired);
    }
}
