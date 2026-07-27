package com.m4trust.coreapi.organization.api;

import com.m4trust.coreapi.organization.domain.*;
import java.util.List;

public record LegalEntityMemberList(List<LegalEntityMember> items) {

  public LegalEntityMemberList {
    items = List.copyOf(items);
  }
}
