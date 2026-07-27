import type { UseQueryResult } from "@tanstack/react-query";

import type {
  LegalEntity,
  LegalEntityMember,
  LegalEntityMemberList,
  LegalEntityMembership,
} from "../organizationApi";
import {
  getOrganizationErrorMessage,
  isInvalidLegalEntitySelection,
} from "../organizationErrors";
import { organizationRoleLabel } from "../organizationLabels";

interface SelectedEntityPanelProps {
  selectedMembership: LegalEntityMembership;
  detailQuery: UseQueryResult<LegalEntity>;
  membersQuery: UseQueryResult<LegalEntityMemberList>;
}

export function SelectedEntityPanel({
  selectedMembership,
  detailQuery,
  membersQuery,
}: SelectedEntityPanelProps) {
  const scopedError = detailQuery.error ?? membersQuery.error;

  return (
    <section className="workspace-panel entity-detail-panel" aria-live="polite">
      <div className="panel-heading">
        <span className="section-kicker">Aktif bağlam</span>
        <h2>{selectedMembership.legalName}</h2>
        <p>
          Bu alandaki işlemler seçili kuruluş için sunucuda yeniden
          yetkilendirilir.
        </p>
      </div>

      {scopedError && !isInvalidLegalEntitySelection(scopedError) ? (
        <p className="form-alert panel-alert" role="alert">
          {getOrganizationErrorMessage(scopedError)}
        </p>
      ) : null}

      {detailQuery.isPending || membersQuery.isPending ? (
        <div className="panel-loading" role="status">
          <span className="loading-line" aria-hidden="true" />
          <p>Kuruluş bilgileri yükleniyor…</p>
        </div>
      ) : null}

      {detailQuery.data ? (
        <dl className="entity-facts">
          <div>
            <dt>Resmî ad</dt>
            <dd>{detailQuery.data.legalName}</dd>
          </div>
          <div>
            <dt>Kayıt numarası</dt>
            <dd>{detailQuery.data.registrationNumber}</dd>
          </div>
          <div>
            <dt>Üyelik rolünüz</dt>
            <dd>{organizationRoleLabel(selectedMembership.role)}</dd>
          </div>
        </dl>
      ) : null}

      {membersQuery.data ? (
        <div className="members-region">
          <h3>Üyeler</h3>
          {membersQuery.data.items.length === 0 ? (
            <p className="muted-copy">Bu kuruluş için üye bulunamadı.</p>
          ) : (
            <ul className="member-list">
              {membersQuery.data.items.map((member: LegalEntityMember) => (
                <li key={member.userId}>
                  <span>
                    <strong>{member.displayName}</strong>
                    <small>{member.email}</small>
                  </span>
                  <span className="role-badge">
                    {organizationRoleLabel(member.role)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </section>
  );
}
