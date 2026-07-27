import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import type { MutableRefObject } from "react";

import type { DealDetail } from "../deals";
import { dealDetailQueryKey } from "../deals";
import {
  acceptEvidence,
  acceptFulfillmentWithoutEvidence,
  getFulfillment,
  rejectEvidence,
  startFulfillment,
  type EvidenceSubmission,
  type FulfillmentDetail,
} from "./fulfillmentApi";
import {
  getFulfillmentErrorMessage,
  shouldResetFulfillmentIdempotencyKey,
} from "./fulfillmentErrors";

export const FULFILLMENT_QUERY_KEY = ["fulfillment"] as const;

/** Non-terminal statuses where a counterparty action may appear without reload. */
export const FULFILLMENT_LIVE_POLL_STATUSES = new Set([
  "IN_PROGRESS",
  "EVIDENCE_REQUIRED",
  "REVIEW_REQUIRED",
]);

export const FULFILLMENT_POLL_INTERVAL_MS = 5_000;

export function fulfillmentDetailQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return [...FULFILLMENT_QUERY_KEY, legalEntityId, dealId] as const;
}

export function fulfillmentDetailQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: fulfillmentDetailQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) => getFulfillment(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId) && enabled,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && FULFILLMENT_LIVE_POLL_STATUSES.has(status)
        ? FULFILLMENT_POLL_INTERVAL_MS
        : false;
    },
  });
}

type FulfillmentMutationParams = {
  legalEntityId: string;
  deal: DealDetail;
  fulfillment: FulfillmentDetail | undefined;
  startKeyRef: MutableRefObject<string | undefined>;
  reviewKeyRef: MutableRefObject<string | undefined>;
  acceptWithoutEvidenceKeyRef: MutableRefObject<string | undefined>;
  onStartSuccess: () => void;
  onStartError: (message: string) => void;
  onFeedbackNotice: (message: string) => void;
  onReviewError: (message: string | undefined) => void;
  onClearRejectionReason: () => void;
};

export function useFulfillmentMutations({
  legalEntityId,
  deal,
  fulfillment,
  startKeyRef,
  reviewKeyRef,
  acceptWithoutEvidenceKeyRef,
  onStartSuccess,
  onStartError,
  onFeedbackNotice,
  onReviewError,
  onClearRejectionReason,
}: FulfillmentMutationParams) {
  const queryClient = useQueryClient();

  function refreshAfterMutation() {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: fulfillmentDetailQueryKey(legalEntityId, deal.id),
    });
  }

  const startMutation = useMutation({
    mutationFn: () => {
      startKeyRef.current ??= crypto.randomUUID();
      return startFulfillment(
        legalEntityId,
        deal.id,
        { expectedVersion: deal.version },
        startKeyRef.current,
      );
    },
    onSuccess: () => {
      startKeyRef.current = undefined;
      onStartSuccess();
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "start")) {
        startKeyRef.current = undefined;
        refreshAfterMutation();
      }
      onStartError(getFulfillmentErrorMessage(error));
    },
  });

  const acceptMutation = useMutation({
    mutationFn: (evidence: EvidenceSubmission) => {
      reviewKeyRef.current ??= crypto.randomUUID();
      return acceptEvidence(
        legalEntityId,
        deal.id,
        evidence.id,
        {
          expectedVersion: deal.version,
          expectedEvidenceVersion: evidence.version,
        },
        reviewKeyRef.current,
      );
    },
    onSuccess: () => {
      reviewKeyRef.current = undefined;
      onReviewError(undefined);
      onFeedbackNotice(
        "Teslimat tamamlandı — kapanış için Kapanış bölümüne geçin",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "review")) {
        reviewKeyRef.current = undefined;
        refreshAfterMutation();
      }
      onReviewError(getFulfillmentErrorMessage(error));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (variables: {
      evidence: EvidenceSubmission;
      reason: string;
    }) => {
      reviewKeyRef.current ??= crypto.randomUUID();
      return rejectEvidence(
        legalEntityId,
        deal.id,
        variables.evidence.id,
        {
          expectedVersion: deal.version,
          expectedEvidenceVersion: variables.evidence.version,
          reason: variables.reason,
        },
        reviewKeyRef.current,
      );
    },
    onSuccess: () => {
      reviewKeyRef.current = undefined;
      onReviewError(undefined);
      onClearRejectionReason();
      onFeedbackNotice(
        "Kanıt reddedildi — satıcının yerine yeni bir yükleme yapması bekleniyor",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetFulfillmentIdempotencyKey(error, "review")) {
        reviewKeyRef.current = undefined;
        refreshAfterMutation();
      }
      onReviewError(getFulfillmentErrorMessage(error));
    },
  });

  const acceptWithoutEvidenceMutation = useMutation({
    mutationFn: () => {
      acceptWithoutEvidenceKeyRef.current ??= crypto.randomUUID();
      return acceptFulfillmentWithoutEvidence(
        legalEntityId,
        deal.id,
        {
          expectedDealVersion: deal.version,
          expectedFulfillmentVersion: fulfillment!.version,
        },
        acceptWithoutEvidenceKeyRef.current,
      );
    },
    onSuccess: () => {
      acceptWithoutEvidenceKeyRef.current = undefined;
      onReviewError(undefined);
      onFeedbackNotice(
        "Teslimat kanıtsız kabul edildi — kapanış için Kapanış bölümüne geçin",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      if (
        shouldResetFulfillmentIdempotencyKey(error, "acceptWithoutEvidence")
      ) {
        acceptWithoutEvidenceKeyRef.current = undefined;
        refreshAfterMutation();
      }
      onReviewError(getFulfillmentErrorMessage(error));
    },
  });

  return {
    startMutation,
    acceptMutation,
    rejectMutation,
    acceptWithoutEvidenceMutation,
  };
}
