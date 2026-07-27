import type { LegalEntityMembership } from "../../features/organization/organizationApi";

export interface DealWorkspaceContext {
  selectedLegalEntityId: string | undefined;
  selectedMembership: LegalEntityMembership | undefined;
  selectionNotice: string | undefined;
  clearInvalidSelection: () => void;
  membershipsPending: boolean;
  membershipsError: unknown;
  membershipsFetching: boolean;
  refetchMemberships: () => void;
}
