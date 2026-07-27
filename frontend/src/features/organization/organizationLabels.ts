import type { LegalEntityMembership } from "./organizationApi";

export function organizationRoleLabel(
  role: LegalEntityMembership["role"],
): string {
  return role === "ADMIN" ? "Yönetici" : "Üye";
}
