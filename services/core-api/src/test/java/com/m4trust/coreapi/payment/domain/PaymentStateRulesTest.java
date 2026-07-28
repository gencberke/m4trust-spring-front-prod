package com.m4trust.coreapi.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentStateRulesTest {

  private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

  @ParameterizedTest(name = "{0} resolves to {1}")
  @MethodSource("operationOutcomes")
  void paymentOperationsResolveWithoutReopening(
      String outcome, PaymentOperationStatus expectedStatus) {
    PaymentOperation operation =
        PaymentOperation.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
    operation.markUnconfirmed(NOW);

    if ("succeeded".equals(outcome)) {
      operation.applySucceeded("provider-ref", NOW);
    } else {
      operation.applyDeclined("provider-ref", NOW);
    }

    assertEquals(expectedStatus, operation.status());
    assertThrows(PaymentOperation.StateConflict.class, () -> operation.markUnconfirmed(NOW));
  }

  @Test
  void fundingMovesThroughPendingToFunded() {
    FundingUnit unit = fundingUnit();
    unit.beginPayment(0, NOW);
    unit.markFunded(1, NOW);

    assertEquals(FundingUnitStatus.FUNDED, unit.status());
    assertThrows(FundingUnit.StateConflict.class, () -> unit.beginPayment(2, NOW));
  }

  @Test
  void failedFundingCanRetryButStaleCommandsCannotMutate() {
    FundingUnit unit = fundingUnit();
    unit.beginPayment(0, NOW);
    unit.markFailed(1, NOW);
    unit.beginPayment(2, NOW);

    assertEquals(FundingUnitStatus.PENDING, unit.status());
    assertThrows(FundingUnit.StaleVersion.class, () -> unit.markFunded(2, NOW));
  }

  private static Stream<Arguments> operationOutcomes() {
    return Stream.of(
        Arguments.of("succeeded", PaymentOperationStatus.SUCCEEDED),
        Arguments.of("declined", PaymentOperationStatus.DECLINED));
  }

  private static FundingUnit fundingUnit() {
    return FundingUnit.create(UUID.randomUUID(), UUID.randomUUID(), 1_000, "TRY", NOW);
  }
}
