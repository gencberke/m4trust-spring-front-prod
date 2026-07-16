import { queryOptions } from "@tanstack/react-query";

import {
  getLegalEntity,
  listLegalEntityMembers,
  listLegalEntityMemberships,
} from "./organizationApi";

export const LEGAL_ENTITY_MEMBERSHIPS_QUERY_KEY = [
  "organization",
  "memberships",
] as const;

export function legalEntityMembershipsQueryOptions() {
  return queryOptions({
    queryKey: LEGAL_ENTITY_MEMBERSHIPS_QUERY_KEY,
    queryFn: ({ signal }) => listLegalEntityMemberships(signal),
  });
}

export function legalEntityDetailQueryOptions(
  legalEntityId: string | undefined,
) {
  return queryOptions({
    queryKey: ["organization", "legal-entity", legalEntityId] as const,
    queryFn: ({ signal }) => getLegalEntity(legalEntityId!, signal),
    enabled: Boolean(legalEntityId),
  });
}

export function legalEntityMembersQueryOptions(
  legalEntityId: string | undefined,
) {
  return queryOptions({
    queryKey: ["organization", "legal-entity", legalEntityId, "members"] as const,
    queryFn: ({ signal }) => listLegalEntityMembers(legalEntityId!, signal),
    enabled: Boolean(legalEntityId),
  });
}
