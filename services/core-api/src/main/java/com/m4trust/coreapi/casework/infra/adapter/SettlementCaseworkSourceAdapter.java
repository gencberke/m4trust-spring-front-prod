package com.m4trust.coreapi.casework.infra.adapter;

import com.m4trust.coreapi.casework.domain.*;
import com.m4trust.coreapi.casework.infra.persistence.*;
import com.m4trust.coreapi.payment.domain.SettlementSourcePorts;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementCaseworkSourceAdapter implements SettlementSourcePorts.CaseworkTarget {

  private final DisputeCaseRepository disputeCases;

  SettlementCaseworkSourceAdapter(DisputeCaseRepository disputeCases) {
    this.disputeCases = disputeCases;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveDispute(UUID dealId) {
    return disputeCases.findActiveByDealId(dealId).isPresent();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockActiveDisputesInOrder(UUID dealId) {
    disputeCases.lockActiveByDealIdInOrder(dealId);
  }
}
