package com.m4trust.coreapi.deal;

import java.util.Set;
import java.util.UUID;

import com.m4trust.coreapi.casework.CaseworkSourcePorts;
import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import org.springframework.stereotype.Service;

/** Deal-owned legal-name projection for casework opening attribution. */
@Service
class CaseworkLegalEntityNameAdapter implements CaseworkSourcePorts.LegalEntityNames {

    private final InvitationLegalEntityQueryPort legalEntities;

    CaseworkLegalEntityNameAdapter(InvitationLegalEntityQueryPort legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Override
    public String requireLegalName(UUID legalEntityId) {
        return legalEntities.findLegalNames(Set.of(legalEntityId)).get(legalEntityId);
    }
}
