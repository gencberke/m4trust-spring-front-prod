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
        return this == DRAFT;
    }

    boolean allowsCancellation() {
        return this == DRAFT;
    }

    boolean allowsDocumentUpload() {
        return this == DRAFT;
    }

    void requireBasicFieldEditingAllowed() {
        if (!allowsBasicFieldEditing()) {
            throw new DealStateConflictException(
                    "Deal basic fields cannot be edited while status is " + this);
        }
    }

    void requirePartyManagementAllowed() {
        if (this != DRAFT) {
            throw new DealStateConflictException(
                    "Deal parties cannot be managed while status is " + this);
        }
    }

    private DealStatus transition(DealAction action) {
        return switch (this) {
            case DRAFT -> switch (action) {
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
