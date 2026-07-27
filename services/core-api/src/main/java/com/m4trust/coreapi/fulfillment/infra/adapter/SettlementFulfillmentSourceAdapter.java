package com.m4trust.coreapi.fulfillment.infra.adapter;

import com.m4trust.coreapi.fulfillment.domain.*;
import com.m4trust.coreapi.fulfillment.domain.port.*;
import com.m4trust.coreapi.fulfillment.infra.persistence.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import com.m4trust.coreapi.payment.domain.SettlementSourcePorts;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementFulfillmentSourceAdapter implements SettlementSourcePorts.FulfillmentTarget {

  private final FulfillmentRepository fulfillmentRepository;

  SettlementFulfillmentSourceAdapter(FulfillmentRepository fulfillmentRepository) {
    this.fulfillmentRepository = fulfillmentRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SettlementSourcePorts.FulfillmentSnapshot> findVisible(
      OperationContext context, UUID dealId) {
    return fulfillmentRepository.findByDealId(dealId).map(this::snapshot);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<SettlementSourcePorts.FulfillmentSnapshot> lockVisible(
      OperationContext context, UUID dealId) {
    return fulfillmentRepository.findByDealIdForUpdate(dealId).map(this::snapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SettlementSourcePorts.FulfillmentSnapshot> findForProjection(UUID dealId) {
    return fulfillmentRepository.findByDealId(dealId).map(this::snapshot);
  }

  private SettlementSourcePorts.FulfillmentSnapshot snapshot(Fulfillment.FulfillmentRecord record) {
    return new SettlementSourcePorts.FulfillmentSnapshot(
        record.id(), record.status().name(), record.version(), record.completedAt());
  }
}
