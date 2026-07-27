package com.m4trust.coreapi.contractintelligence.infra.adapter;

import com.m4trust.coreapi.contractintelligence.api.*;
import com.m4trust.coreapi.contractintelligence.api.dto.*;
import com.m4trust.coreapi.contractintelligence.domain.*;
import com.m4trust.coreapi.contractintelligence.infra.*;
import com.m4trust.coreapi.integration.infra.messaging.AiResultsMessageRouter;
import com.m4trust.coreapi.integration.infra.messaging.IntegrationViolation;
import org.springframework.stereotype.Service;

/** Backward-compatible entry for document result integration tests. */
@Service
final class AnalysisResultConsumer {

  private final AiResultsMessageRouter router;

  AnalysisResultConsumer(AiResultsMessageRouter router) {
    this.router = router;
  }

  void consume(String raw) {
    try {
      router.consume(raw);
    } catch (com.m4trust.coreapi.integration.infra.messaging.IntegrationViolation exception) {
      throw new IntegrationViolation();
    }
  }

  static final class IntegrationViolation
      extends com.m4trust.coreapi.integration.infra.messaging.IntegrationViolation {}
}
