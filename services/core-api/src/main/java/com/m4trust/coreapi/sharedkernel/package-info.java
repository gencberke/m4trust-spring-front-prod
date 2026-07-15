/**
 * Small, stable concepts that are genuinely shared across business modules.
 *
 * <p>This module is reserved for cross-module primitives such as stable IDs,
 * money, domain-event contracts, clocks, correlation context, and base domain
 * exceptions when those concepts are introduced by a slice. It is not a home
 * for generic utilities, business entities, or module-specific rules.
 */
package com.m4trust.coreapi.sharedkernel;
