package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import java.util.List;

record IncomingDealInvitationPage(
    List<IncomingDealInvitation> items, int page, int size, long totalElements, int totalPages) {
  IncomingDealInvitationPage {
    items = List.copyOf(items);
  }
}
