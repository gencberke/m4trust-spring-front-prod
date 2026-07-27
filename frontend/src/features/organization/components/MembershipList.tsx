import type { LegalEntityMembership } from "../organizationApi";
import { organizationRoleLabel } from "../organizationLabels";

interface MembershipListProps {
  memberships: LegalEntityMembership[];
  selectedLegalEntityId: string | undefined;
  onSelect: (legalEntityId: string) => void;
}

export function MembershipList({
  memberships,
  selectedLegalEntityId,
  onSelect,
}: MembershipListProps) {
  return (
    <section className="workspace-panel" aria-labelledby="entities-title">
      <div className="panel-heading">
        <span className="section-kicker">Organizasyonlar</span>
        <h2 id="entities-title">Kuruluşlarım</h2>
        <p>Üyesi olduğunuz çalışma alanlarından birini aktif hale getirin.</p>
      </div>
      <div className="entity-list">
        {memberships.map((membership) => {
          const selected = membership.legalEntityId === selectedLegalEntityId;
          return (
            <button
              className="entity-list-item"
              data-selected={selected}
              key={membership.legalEntityId}
              type="button"
              onClick={() => onSelect(membership.legalEntityId)}
              aria-pressed={selected}
            >
              <span>
                <strong>{membership.legalName}</strong>
                <small>{membership.registrationNumber}</small>
              </span>
              <span className="role-badge">
                {organizationRoleLabel(membership.role)}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
