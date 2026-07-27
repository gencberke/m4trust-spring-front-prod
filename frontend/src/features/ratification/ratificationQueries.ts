import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import type { MutableRefObject } from "react";

import type { DealDetail } from "../deals/dealApi";
import { dealDetailQueryKey } from "../deals/dealQueries";
import { getRuleSetVersion } from "../review/reviewApi";
import {
  approveRatificationPackage,
  createRatificationPackage,
  listRatificationPackages,
  rejectRatificationPackage,
  type RatificationCommercialTerms,
  type RatificationPackageDetail,
} from "./ratificationApi";
import {
  shouldRefetchAfterActionError,
  shouldRefetchAfterCreateError,
  shouldResetRatificationIdempotencyKey,
} from "./ratificationErrors";
import type { EvidencePolicy } from "./ratificationPresentation";

export const RATIFICATION_QUERY_KEY = ["ratification-packages"] as const;

export function ratificationPackageHistoryQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return [...RATIFICATION_QUERY_KEY, legalEntityId, dealId] as const;
}

export function ratificationPackageHistoryQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
) {
  return queryOptions({
    queryKey: ratificationPackageHistoryQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      listRatificationPackages(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId),
  });
}

export function ratificationRuleSetVersionQueryOptions(
  legalEntityId: string,
  dealId: string,
  ruleSetVersionId: string | undefined,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: ["rule-set", legalEntityId, dealId, ruleSetVersionId],
    queryFn: ({ signal }) =>
      getRuleSetVersion(legalEntityId, dealId, ruleSetVersionId!, signal),
    enabled: Boolean(ruleSetVersionId) && enabled,
  });
}

type RatificationMutationParams = {
  legalEntityId: string;
  deal: DealDetail;
  createKeyRef: MutableRefObject<string | undefined>;
  approveKeyRef: MutableRefObject<string | undefined>;
  rejectKeyRef: MutableRefObject<string | undefined>;
  onNotice: (notice: string) => void;
  onConfirmationClose: () => void;
};

export function useRatificationMutations({
  legalEntityId,
  deal,
  createKeyRef,
  approveKeyRef,
  rejectKeyRef,
  onNotice,
  onConfirmationClose,
}: RatificationMutationParams) {
  const queryClient = useQueryClient();
  const refreshAfterMutation = () => {
    void queryClient.invalidateQueries({
      queryKey: dealDetailQueryKey(legalEntityId, deal.id),
    });
    void queryClient.invalidateQueries({
      queryKey: ratificationPackageHistoryQueryKey(legalEntityId, deal.id),
    });
  };
  const createMutation = useMutation({
    mutationFn: (payload: {
      commercialTerms: RatificationCommercialTerms;
      disputeWindowDays: number;
      evidencePolicy: EvidencePolicy;
    }) => {
      createKeyRef.current ??= crypto.randomUUID();
      return createRatificationPackage(
        legalEntityId,
        deal.id,
        {
          expectedVersion: deal.version,
          commercialTerms: payload.commercialTerms,
          disputeWindowDays: payload.disputeWindowDays,
          evidencePolicy: payload.evidencePolicy,
        },
        createKeyRef.current,
      );
    },
    onSuccess: () => {
      createKeyRef.current = undefined;
      onNotice(
        "Ticari koşullar onaya sunuldu; diğer tarafın onayı bekleniyor.",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      if (shouldResetRatificationIdempotencyKey(error))
        createKeyRef.current = undefined;
      if (shouldRefetchAfterCreateError(error)) refreshAfterMutation();
    },
  });
  const approveMutation = useMutation({
    mutationFn: (targetPackage: RatificationPackageDetail) => {
      approveKeyRef.current ??= crypto.randomUUID();
      return approveRatificationPackage(
        legalEntityId,
        deal.id,
        targetPackage.id,
        { expectedPackageVersion: targetPackage.version },
        approveKeyRef.current,
      );
    },
    onSuccess: (updated) => {
      approveKeyRef.current = undefined;
      onConfirmationClose();
      onNotice(
        updated.status === "RATIFIED"
          ? "Her iki tarafın onayı kaydedildi."
          : "Onayınız kaydedildi; diğer tarafın onayı bekleniyor.",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      onConfirmationClose();
      if (shouldResetRatificationIdempotencyKey(error))
        approveKeyRef.current = undefined;
      if (shouldRefetchAfterActionError(error)) refreshAfterMutation();
    },
  });
  const rejectMutation = useMutation({
    mutationFn: (targetPackage: RatificationPackageDetail) => {
      rejectKeyRef.current ??= crypto.randomUUID();
      return rejectRatificationPackage(
        legalEntityId,
        deal.id,
        targetPackage.id,
        { expectedPackageVersion: targetPackage.version },
        rejectKeyRef.current,
      );
    },
    onSuccess: () => {
      rejectKeyRef.current = undefined;
      onConfirmationClose();
      onNotice(
        "Ticari koşullar reddedildi. Devam etmek için yeniden onaya sunulmalıdır.",
      );
      refreshAfterMutation();
    },
    onError: (error) => {
      onConfirmationClose();
      if (shouldResetRatificationIdempotencyKey(error))
        rejectKeyRef.current = undefined;
      if (shouldRefetchAfterActionError(error)) refreshAfterMutation();
    },
  });
  return { createMutation, approveMutation, rejectMutation };
}
