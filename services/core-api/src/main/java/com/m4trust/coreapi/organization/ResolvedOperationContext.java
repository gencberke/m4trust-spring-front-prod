package com.m4trust.coreapi.organization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResolvedOperationContext {

    RequestedOperation value();

    /**
     * Optional path variable that must equal the active legal-entity header.
     * Future scoped resources whose paths do not contain a legal-entity ID can
     * leave this empty and still reuse membership verification.
     */
    String legalEntityPathVariable() default "";
}
