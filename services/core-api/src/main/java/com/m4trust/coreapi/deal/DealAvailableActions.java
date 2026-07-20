package com.m4trust.coreapi.deal;

record DealAvailableActions(boolean canUpdate, boolean canCancel,
        boolean canCreateInvitation, boolean canManageParties,
        boolean canCreateDocumentUploadIntent, boolean canRequestAnalysis,
        boolean canReviewExtraction, boolean canCreateRatificationPackage,
        boolean canApproveRatification, boolean canRejectRatification,
        boolean canCreateFundingPlan, boolean canInitiateFunding,
        boolean canReconcilePaymentOperation) {
}
