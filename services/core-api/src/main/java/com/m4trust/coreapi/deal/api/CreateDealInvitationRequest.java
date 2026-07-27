package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import com.m4trust.coreapi.identity.domain.EmailAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateDealInvitationRequest(
    @NotBlank(message = "Recipient email is required.")
        @Email(message = "Recipient email must be valid.")
        @Size(max = 320, message = "Recipient email must not exceed 320 characters.")
        String recipientEmail) {

  CreateDealInvitationRequest {
    recipientEmail = EmailAddress.normalize(recipientEmail);
  }
}
