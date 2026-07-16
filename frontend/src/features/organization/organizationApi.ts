import type { components } from "../../generated/core-api";
import {
  postJsonWithFreshCsrf,
  requestJson,
} from "../../app/coreApi";

export type CreateLegalEntityRequest =
  components["schemas"]["CreateLegalEntityRequest"];
export type LegalEntity = components["schemas"]["LegalEntity"];
export type LegalEntityMember = components["schemas"]["LegalEntityMember"];
export type LegalEntityMemberList =
  components["schemas"]["LegalEntityMemberList"];
export type LegalEntityMembership =
  components["schemas"]["LegalEntityMembership"];

type LegalEntityMembershipList =
  components["schemas"]["LegalEntityMembershipList"];

export function listLegalEntityMemberships(
  signal?: AbortSignal,
): Promise<LegalEntityMembershipList> {
  return requestJson<LegalEntityMembershipList>("/legal-entities", { signal });
}

export function createLegalEntity(
  request: CreateLegalEntityRequest,
): Promise<LegalEntity> {
  return postJsonWithFreshCsrf<LegalEntity>("/legal-entities", request);
}

export function getLegalEntity(
  legalEntityId: string,
  signal?: AbortSignal,
): Promise<LegalEntity> {
  return requestJson<LegalEntity>(`/legal-entities/${legalEntityId}`, {
    signal,
  });
}

export function listLegalEntityMembers(
  legalEntityId: string,
  signal?: AbortSignal,
): Promise<LegalEntityMemberList> {
  return requestJson<LegalEntityMemberList>(
    `/legal-entities/${legalEntityId}/members`,
    { signal },
  );
}
