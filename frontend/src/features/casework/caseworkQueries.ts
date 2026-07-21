import { queryOptions } from "@tanstack/react-query";

import {
  getDispute,
  listDisputeComments,
  listDisputes,
  type DisputeCommentSort,
  type DisputeSort,
} from "./caseworkApi";

export const CASEWORK_QUERY_KEY = ["casework"] as const;

export function disputeHistoryQueryKey(legalEntityId: string, dealId: string) {
  return [...CASEWORK_QUERY_KEY, legalEntityId, dealId, "history"] as const;
}

export function disputeDetailQueryKey(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
) {
  return [...CASEWORK_QUERY_KEY, legalEntityId, dealId, "detail", disputeId] as const;
}

export function disputeCommentsQueryKey(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
  page: number,
) {
  return [
    ...CASEWORK_QUERY_KEY,
    legalEntityId,
    dealId,
    "comments",
    disputeId,
    page,
  ] as const;
}

export function disputeHistoryQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  enabled: boolean,
  page = 0,
  sort: DisputeSort = "openedAt,desc",
) {
  return queryOptions({
    queryKey: [
      ...disputeHistoryQueryKey(legalEntityId ?? "unselected", dealId ?? "missing"),
      page,
      sort,
    ] as const,
    queryFn: ({ signal }) =>
      listDisputes(legalEntityId!, dealId!, page, sort, signal),
    enabled: Boolean(legalEntityId && dealId) && enabled,
  });
}

export function disputeDetailQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  disputeId: string | undefined,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: disputeDetailQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
      disputeId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      getDispute(legalEntityId!, dealId!, disputeId!, signal),
    enabled: Boolean(legalEntityId && dealId && disputeId) && enabled,
  });
}

export function disputeCommentsQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  disputeId: string | undefined,
  enabled: boolean,
  page: number,
  sort: DisputeCommentSort = "createdAt,asc",
) {
  return queryOptions({
    queryKey: disputeCommentsQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
      disputeId ?? "missing",
      page,
    ),
    queryFn: ({ signal }) =>
      listDisputeComments(legalEntityId!, dealId!, disputeId!, page, sort, signal),
    enabled: Boolean(legalEntityId && dealId && disputeId) && enabled,
  });
}
