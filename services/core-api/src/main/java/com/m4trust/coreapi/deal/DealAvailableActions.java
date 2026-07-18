package com.m4trust.coreapi.deal;

record DealAvailableActions(boolean canUpdate, boolean canCancel,
        boolean canCreateInvitation, boolean canManageParties,
        boolean canCreateDocumentUploadIntent) {
}
