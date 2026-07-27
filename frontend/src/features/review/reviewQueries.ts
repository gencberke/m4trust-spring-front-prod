import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import type { MutableRefObject } from "react";
import type { components } from "../../generated/core-api";
import type { DealDetail } from "../deals";
import {
  acceptExtractionReview,
  getExtractionReview,
  getRuleSetVersion,
  listRuleSets,
  type DealExtractionReview,
} from "./reviewApi";
import {
  shouldRefetchReview,
  shouldResetReviewIdempotencyKey,
} from "./reviewErrors";
import { changed, initialDraft, toRuleValue, type Draft } from "./reviewTypes";

export const REVIEW_QUERY_KEY = ["review"] as const;
export const RULE_SET_HISTORY_QUERY_KEY = ["rule-sets"] as const;
export const RULE_SET_QUERY_KEY = ["rule-set"] as const;

export function reviewQueryOptions(
  legalEntityId: string,
  dealId: string,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: [...REVIEW_QUERY_KEY, legalEntityId, dealId],
    queryFn: ({ signal }) => getExtractionReview(legalEntityId, dealId, signal),
    enabled,
  });
}

export function ruleSetHistoryQueryOptions(
  legalEntityId: string,
  dealId: string,
) {
  return queryOptions({
    queryKey: [...RULE_SET_HISTORY_QUERY_KEY, legalEntityId, dealId],
    queryFn: ({ signal }) => listRuleSets(legalEntityId, dealId, signal),
  });
}

export function ruleSetVersionQueryOptions(
  legalEntityId: string,
  dealId: string,
  selectedVersion: string | undefined,
) {
  return queryOptions({
    queryKey: [...RULE_SET_QUERY_KEY, legalEntityId, dealId, selectedVersion],
    queryFn: ({ signal }) =>
      getRuleSetVersion(legalEntityId, dealId, selectedVersion!, signal),
    enabled: Boolean(selectedVersion),
  });
}

type AcceptReviewParams = {
  legalEntityId: string;
  deal: DealDetail;
  review?: DealExtractionReview;
  drafts: Record<string, Draft>;
  added: Draft[];
  requestKeyRef: MutableRefObject<string | undefined>;
  onConfirmationClose: () => void;
};

export function useAcceptExtractionReview({
  legalEntityId,
  deal,
  review,
  drafts,
  added,
  requestKeyRef,
  onConfirmationClose,
}: AcceptReviewParams) {
  const queryClient = useQueryClient();
  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: [...REVIEW_QUERY_KEY, legalEntityId, deal.id],
      }),
      queryClient.invalidateQueries({
        queryKey: [...RULE_SET_HISTORY_QUERY_KEY, legalEntityId, deal.id],
      }),
      queryClient.invalidateQueries({
        queryKey: ["deals", legalEntityId, "detail", deal.id],
      }),
    ]);
  return useMutation({
    mutationFn: () => {
      if (!review) throw new Error("Review is unavailable");
      requestKeyRef.current ??= crypto.randomUUID();
      const decisions: components["schemas"]["ReviewRuleDecision"][] =
        review.rules.map((rule) => {
          const draft = drafts[rule.ruleReference] ?? initialDraft(rule);
          if (draft.excluded)
            return {
              decision: "EXCLUDED" as const,
              ruleReference: rule.ruleReference,
            };
          const structuredValue = toRuleValue(draft.value);
          return changed(rule, draft) && structuredValue
            ? {
                decision: "MODIFIED" as const,
                ruleReference: rule.ruleReference,
                category: draft.category,
                title: draft.title.trim(),
                description: draft.description.trim(),
                structuredValue,
              }
            : { decision: "KEPT" as const, ruleReference: rule.ruleReference };
        });
      for (const draft of added) {
        const structuredValue = toRuleValue(draft.value);
        if (!structuredValue) throw new Error("Invalid added rule");
        decisions.push({
          decision: "ADDED",
          category: draft.category,
          title: draft.title.trim(),
          description: draft.description.trim(),
          structuredValue,
        });
      }
      return acceptExtractionReview(
        legalEntityId,
        deal.id,
        {
          analysisId: review.analysisId,
          expectedVersion: deal.version,
          decisions,
        },
        requestKeyRef.current,
      );
    },
    onSuccess: async () => {
      requestKeyRef.current = undefined;
      onConfirmationClose();
      await invalidate();
    },
    onError: async (error) => {
      onConfirmationClose();
      if (shouldResetReviewIdempotencyKey(error))
        requestKeyRef.current = undefined;
      if (shouldRefetchReview(error)) await invalidate();
    },
  });
}
