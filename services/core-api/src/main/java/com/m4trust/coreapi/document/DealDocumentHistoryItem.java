package com.m4trust.coreapi.document;

/** Closed union matching the DealDocumentHistory.items oneOf schema. */
sealed interface DealDocumentHistoryItem permits PendingDealDocument, HistoricalDealDocument {
}
