package com.m4trust.coreapi.payment;

/** Service-layer exceptions mapped 1:1 to the frozen Problem Details codes by {@link PaymentExceptionHandler}. */
final class PaymentExceptions {
    private PaymentExceptions() { }

    static final class DealNotFound extends RuntimeException { }

    static final class FundingPlanNotFound extends RuntimeException { }

    static final class FundingUnitNotFound extends RuntimeException { }

    static final class PaymentOperationNotFound extends RuntimeException { }

    static final class FundingMutationForbidden extends RuntimeException { }

    static final class DealStateConflict extends RuntimeException { }

    static final class DealStaleVersion extends RuntimeException { }

    static final class FundingPlanAlreadyExists extends RuntimeException { }

    static final class FundingUnitStaleVersion extends RuntimeException { }

    static final class FundingUnitAlreadyFunded extends RuntimeException { }

    static final class PaymentOperationInFlight extends RuntimeException { }

    static final class PaymentOperationStaleVersion extends RuntimeException { }

    static final class PaymentOperationStateConflict extends RuntimeException { }
}
