import type { components } from "../../generated/core-api";
import {
  postJsonWithFreshCsrf,
  requestJson,
} from "../../app/coreApi";

export type AcceptDealInvitationRequest =
  components["schemas"]["AcceptDealInvitationRequest"];
export type CreateDealInvitationRequest =
  components["schemas"]["CreateDealInvitationRequest"];
export type DealInvitation = components["schemas"]["DealInvitation"];
export type DealInvitationPage =
  components["schemas"]["DealInvitationPage"];
export type DealInvitationTerminalActionRequest =
  components["schemas"]["DealInvitationTerminalActionRequest"];
export type IncomingDealInvitation =
  components["schemas"]["IncomingDealInvitation"];
export type IncomingDealInvitationPage =
  components["schemas"]["IncomingDealInvitationPage"];

export interface InvitationListParameters {
  page: number;
  size: number;
}

function invitationSearch(parameters: InvitationListParameters): string {
  return new URLSearchParams({
    page: String(parameters.page),
    size: String(parameters.size),
  }).toString();
}

export function listIncomingDealInvitations(
  parameters: InvitationListParameters,
  signal?: AbortSignal,
): Promise<IncomingDealInvitationPage> {
  return requestJson<IncomingDealInvitationPage>(
    `/deal-invitations/incoming?${invitationSearch(parameters)}`,
    { signal, suppressLegalEntityContext: true },
  );
}

export function acceptDealInvitation(
  invitationId: string,
  request: AcceptDealInvitationRequest,
): Promise<IncomingDealInvitation> {
  return postJsonWithFreshCsrf<IncomingDealInvitation>(
    `/deal-invitations/${invitationId}/accept`,
    request,
    undefined,
    { suppressLegalEntityContext: true },
  );
}

export function rejectDealInvitation(
  invitationId: string,
  request: DealInvitationTerminalActionRequest,
): Promise<IncomingDealInvitation> {
  return postJsonWithFreshCsrf<IncomingDealInvitation>(
    `/deal-invitations/${invitationId}/reject`,
    request,
    undefined,
    { suppressLegalEntityContext: true },
  );
}

export function listDealInvitations(
  legalEntityId: string,
  dealId: string,
  parameters: InvitationListParameters,
  signal?: AbortSignal,
): Promise<DealInvitationPage> {
  return requestJson<DealInvitationPage>(
    `/deals/${dealId}/invitations?${invitationSearch(parameters)}`,
    { signal, headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId } },
  );
}

export function createDealInvitation(
  legalEntityId: string,
  dealId: string,
  request: CreateDealInvitationRequest,
  idempotencyKey: string,
): Promise<DealInvitation> {
  return postJsonWithFreshCsrf<DealInvitation>(
    `/deals/${dealId}/invitations`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function revokeDealInvitation(
  legalEntityId: string,
  invitationId: string,
  request: DealInvitationTerminalActionRequest,
): Promise<DealInvitation> {
  return postJsonWithFreshCsrf<DealInvitation>(
    `/deal-invitations/${invitationId}/revoke`,
    request,
    { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  );
}
