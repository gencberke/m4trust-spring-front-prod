package com.m4trust.coreapi.payment;

/** Service-layer exceptions mapped 1:1 to settlement Problem Details codes. */
final class SettlementExceptions {
    private SettlementExceptions() { }

    static final class DealNotFound extends RuntimeException { }

    static final class SettlementNotFound extends RuntimeException { }

    static final class ReleaseOperationNotFound extends RuntimeException { }

    static final class MutationForbidden extends RuntimeException { }

    static final class DealStateConflict extends RuntimeException { }

    static final class DealStaleVersion extends RuntimeException { }

    static final class SettlementStaleVersion extends RuntimeException { }

    static final class FulfillmentStaleVersion extends RuntimeException { }

    static final class FundingUnitStaleVersion extends RuntimeException { }

    static final class ReleaseOperationStaleVersion extends RuntimeException { }

    static final class ContractualWindowMissing extends RuntimeException { }

    static final class DisputeWindowNotElapsed extends RuntimeException { }

    static final class ActiveDispute extends RuntimeException { }

    static final class AlreadyTerminal extends RuntimeException { }

    static final class OperationAlreadyExists extends RuntimeException { }

    static final class OutcomeUnknown extends RuntimeException { }

    static final class ReconciliationUnavailable extends RuntimeException { }
}
