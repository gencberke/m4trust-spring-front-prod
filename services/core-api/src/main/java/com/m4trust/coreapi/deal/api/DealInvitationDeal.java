package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import java.util.UUID;

record DealInvitationDeal(UUID id, String reference, String title, String initiatorLegalName) {}
