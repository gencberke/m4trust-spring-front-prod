import { queryOptions } from "@tanstack/react-query";

import {
  listDealInvitations,
  listIncomingDealInvitations,
  type InvitationListParameters,
} from "./invitationApi";

export const INVITATION_QUERY_KEY = ["deal-invitations"] as const;

export function incomingInvitationsQueryKey(parameters: InvitationListParameters) {
  return [...INVITATION_QUERY_KEY, "incoming", parameters.page, parameters.size] as const;
}

export function incomingInvitationsQueryOptions(parameters: InvitationListParameters) {
  return queryOptions({
    queryKey: incomingInvitationsQueryKey(parameters),
    queryFn: ({ signal }) => listIncomingDealInvitations(parameters, signal),
  });
}

export function dealInvitationsQueryKey(
  legalEntityId: string,
  dealId: string,
  parameters: InvitationListParameters,
) {
  return [
    ...INVITATION_QUERY_KEY,
    legalEntityId,
    "deal",
    dealId,
    parameters.page,
    parameters.size,
  ] as const;
}

export function dealInvitationsQueryOptions(
  legalEntityId: string | undefined,
  dealId: string,
  parameters: InvitationListParameters,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: dealInvitationsQueryKey(
      legalEntityId ?? "unselected",
      dealId,
      parameters,
    ),
    queryFn: ({ signal }) =>
      listDealInvitations(legalEntityId!, dealId, parameters, signal),
    enabled: Boolean(legalEntityId && enabled),
  });
}
