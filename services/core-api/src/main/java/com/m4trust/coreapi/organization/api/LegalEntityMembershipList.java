package com.m4trust.coreapi.organization.api;

import com.m4trust.coreapi.organization.domain.*;
import java.util.List;

public record LegalEntityMembershipList(List<LegalEntityMembership> items) {

  public LegalEntityMembershipList {
    items = List.copyOf(items);
  }
}
