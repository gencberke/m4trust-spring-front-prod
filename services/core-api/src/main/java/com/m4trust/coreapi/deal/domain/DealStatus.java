package com.m4trust.coreapi.deal.domain;

public enum DealStatus {
  DRAFT,
  ACTIVE,
  CANCELLED,
  COMPLETED,
  ARCHIVED;

  public DealStatus activate() {
    return transition(DealAction.ACTIVATE);
  }

  public DealStatus cancel() {
    return transition(DealAction.CANCEL);
  }

  public DealStatus complete() {
    return transition(DealAction.COMPLETE);
  }

  public DealStatus archive() {
    return transition(DealAction.ARCHIVE);
  }

  public boolean allowsBasicFieldEditing() {
    return this == DRAFT;
  }

  public boolean allowsCancellation() {
    return this == DRAFT;
  }

  public boolean allowsDocumentUpload() {
    return this == DRAFT;
  }

  public void requireBasicFieldEditingAllowed() {
    if (!allowsBasicFieldEditing()) {
      throw new DealStateConflictException(
          "Deal basic fields cannot be edited while status is " + this);
    }
  }

  public void requirePartyManagementAllowed() {
    if (this != DRAFT) {
      throw new DealStateConflictException(
          "Deal parties cannot be managed while status is " + this);
    }
  }

  private DealStatus transition(DealAction action) {
    return switch (this) {
      case DRAFT ->
          switch (action) {
            case ACTIVATE -> ACTIVE;
            case CANCEL -> CANCELLED;
            default -> throw invalidTransition(action);
          };
      case ACTIVE -> {
        if (action == DealAction.COMPLETE) {
          yield COMPLETED;
        }
        throw invalidTransition(action);
      }
      case CANCELLED, COMPLETED -> {
        if (action == DealAction.ARCHIVE) {
          yield ARCHIVED;
        }
        throw invalidTransition(action);
      }
      case ARCHIVED -> throw invalidTransition(action);
    };
  }

  private DealStateConflictException invalidTransition(DealAction action) {
    return new DealStateConflictException(
        "Deal cannot " + action.operationName + " from status " + this);
  }

  private enum DealAction {
    ACTIVATE("activate"),
    CANCEL("cancel"),
    COMPLETE("complete"),
    ARCHIVE("archive");

    private final String operationName;

    DealAction(String operationName) {
      this.operationName = operationName;
    }
  }
}
