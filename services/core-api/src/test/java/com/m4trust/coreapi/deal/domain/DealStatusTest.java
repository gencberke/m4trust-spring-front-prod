package com.m4trust.coreapi.deal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DealStatusTest {

  @ParameterizedTest(name = "{0} {1} -> {2}")
  @MethodSource("criticalTransitions")
  void enforcesTheCriticalLifecycleTable(
      DealStatus current, String action, DealStatus expectedStatus) {
    if (expectedStatus == null) {
      assertThrows(DealStateConflictException.class, () -> transition(current, action));
      return;
    }
    assertEquals(expectedStatus, transition(current, action));
  }

  private static Stream<Arguments> criticalTransitions() {
    return Stream.of(
        Arguments.of(DealStatus.DRAFT, "activate", DealStatus.ACTIVE),
        Arguments.of(DealStatus.DRAFT, "cancel", DealStatus.CANCELLED),
        Arguments.of(DealStatus.ACTIVE, "complete", DealStatus.COMPLETED),
        Arguments.of(DealStatus.CANCELLED, "archive", DealStatus.ARCHIVED),
        Arguments.of(DealStatus.COMPLETED, "archive", DealStatus.ARCHIVED),
        Arguments.of(DealStatus.DRAFT, "complete", null),
        Arguments.of(DealStatus.ACTIVE, "cancel", null),
        Arguments.of(DealStatus.ARCHIVED, "archive", null));
  }

  private static DealStatus transition(DealStatus current, String action) {
    return switch (action) {
      case "activate" -> current.activate();
      case "cancel" -> current.cancel();
      case "complete" -> current.complete();
      case "archive" -> current.archive();
      default -> throw new IllegalArgumentException("unknown action: " + action);
    };
  }
}
