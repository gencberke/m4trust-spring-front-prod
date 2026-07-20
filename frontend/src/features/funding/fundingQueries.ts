import { queryOptions } from "@tanstack/react-query";

import { getFundingPlan, getPaymentOperation } from "./fundingApi";

export const FUNDING_PLAN_QUERY_KEY = ["funding-plan"] as const;
export const PAYMENT_OPERATION_QUERY_KEY = ["payment-operation"] as const;

export function fundingPlanQueryKey(legalEntityId: string, dealId: string) {
  return [...FUNDING_PLAN_QUERY_KEY, legalEntityId, dealId] as const;
}

/**
 * `enabled` should be gated on `deal.funding` carrying a non-null
 * `fundingPlanId` — the plan resource does not exist yet before the buyer
 * ADMIN explicitly creates it (`GET` 404s with `FUNDING_PLAN_NOT_FOUND`).
 */
export function fundingPlanQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: fundingPlanQueryKey(legalEntityId ?? "unselected", dealId ?? "missing"),
    queryFn: ({ signal }) => getFundingPlan(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId) && enabled,
  });
}

export function paymentOperationQueryKey(
  legalEntityId: string,
  paymentOperationId: string,
) {
  return [...PAYMENT_OPERATION_QUERY_KEY, legalEntityId, paymentOperationId] as const;
}

/**
 * Polling is driven by the caller via `refetchInterval` on the returned
 * options (status-dependent: CREATED, or UNCONFIRMED right after a
 * reconcile dispatch) — this factory only fixes the query identity/fetcher.
 */
export function paymentOperationQueryOptions(
  legalEntityId: string | undefined,
  paymentOperationId: string | undefined,
) {
  return queryOptions({
    queryKey: paymentOperationQueryKey(
      legalEntityId ?? "unselected",
      paymentOperationId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      getPaymentOperation(legalEntityId!, paymentOperationId!, signal),
    enabled: Boolean(legalEntityId && paymentOperationId),
  });
}
