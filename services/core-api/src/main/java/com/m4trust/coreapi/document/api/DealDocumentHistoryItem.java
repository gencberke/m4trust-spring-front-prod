package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;

/** Closed union matching the DealDocumentHistory.items oneOf schema. */
sealed interface DealDocumentHistoryItem permits PendingDealDocument, HistoricalDealDocument {}
