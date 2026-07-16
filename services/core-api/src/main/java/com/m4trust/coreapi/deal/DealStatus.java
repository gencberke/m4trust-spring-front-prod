package com.m4trust.coreapi.deal;

enum DealStatus {
    DRAFT,
    ACTIVE,
    CANCELLED,
    COMPLETED,
    ARCHIVED;

    DealStatus activate() {
        return transition(DealAction.ACTIVATE);
    }

    DealStatus cancel() {
        return transition(DealAction.CANCEL);
    }

    DealStatus complete() {
        return transition(DealAction.COMPLETE);
    }

    DealStatus archive() {
        return transition(DealAction.ARCHIVE);
    }

    boolean allowsBasicFieldEditing() {
        return this == DRAFT || this == ACTIVE;
    }

    void requireBasicFieldEditingAllowed() {
        if (!allowsBasicFieldEditing()) {
            throw new DealStateConflictException(
                    "Deal basic fields cannot be edited while status is " + this);
        }
    }

    private DealStatus transition(DealAction action) {
        return switch (this) {
            case DRAFT -> switch (action) {
                case ACTIVATE -> ACTIVE;
                case CANCEL -> CANCELLED;
                default -> throw invalidTransition(action);
            };
            case ACTIVE -> switch (action) {
                case CANCEL -> CANCELLED;
                case COMPLETE -> COMPLETED;
                default -> throw invalidTransition(action);
            };
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
