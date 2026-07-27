import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { CURRENT_USER_QUERY_KEY } from "../../auth";
import {
  createLegalEntity,
  type CreateLegalEntityRequest,
  type LegalEntityMembership,
} from "../organizationApi";
import {
  getOrganizationErrorMessage,
  isInvalidLegalEntitySelection,
} from "../organizationErrors";
import {
  LEGAL_ENTITY_MEMBERSHIPS_QUERY_KEY,
  legalEntityDetailQueryOptions,
  legalEntityMembersQueryOptions,
} from "../organizationQueries";
import { CreateLegalEntityForm } from "./CreateLegalEntityForm";
import { MembershipList } from "./MembershipList";
import { SelectedEntityPanel } from "./SelectedEntityPanel";
import styles from "../Organization.module.css";

export interface OrganizationWorkspaceContext {
  memberships: LegalEntityMembership[];
  membershipsPending: boolean;
  membershipsError: unknown;
  membershipsFetching: boolean;
  selectedLegalEntityId: string | undefined;
  selectedMembership: LegalEntityMembership | undefined;
  selectionNotice: string | undefined;
  selectLegalEntity: (legalEntityId: string | undefined) => void;
  clearInvalidSelection: () => void;
  refetchMemberships: () => void;
}

interface OrganizationWorkspaceProps {
  workspace: OrganizationWorkspaceContext;
}

export function OrganizationWorkspace({
  workspace,
}: OrganizationWorkspaceProps) {
  const clearInvalidSelection = workspace.clearInvalidSelection;
  const queryClient = useQueryClient();
  const [creationNotice, setCreationNotice] = useState<string>();
  const detailQuery = useQuery(
    legalEntityDetailQueryOptions(workspace.selectedMembership?.legalEntityId),
  );
  const membersQuery = useQuery(
    legalEntityMembersQueryOptions(workspace.selectedMembership?.legalEntityId),
  );
  const invalidScopedSelection =
    isInvalidLegalEntitySelection(detailQuery.error) ||
    isInvalidLegalEntitySelection(membersQuery.error);

  useEffect(() => {
    if (invalidScopedSelection) {
      clearInvalidSelection();
    }
  }, [clearInvalidSelection, invalidScopedSelection]);

  const createMutation = useMutation({
    mutationFn: createLegalEntity,
    onSuccess: async (createdEntity) => {
      setCreationNotice(
        `${createdEntity.legalName} oluşturuldu ve aktif kuruluş olarak seçildi.`,
      );
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: LEGAL_ENTITY_MEMBERSHIPS_QUERY_KEY,
        }),
        queryClient.invalidateQueries({
          queryKey: CURRENT_USER_QUERY_KEY,
        }),
      ]);
      workspace.selectLegalEntity(createdEntity.id);
    },
  });

  function submitCreateEntity(
    request: CreateLegalEntityRequest,
    form: HTMLFormElement,
  ) {
    setCreationNotice(undefined);
    createMutation.mutate(request, {
      onSuccess: () => form.reset(),
    });
  }

  return (
    <main className={`workspace-main ${styles.workspace}`}>
      <div className="workspace-column">
        <span className="section-kicker">Organizasyon çalışma alanı</span>
        <h1>Kuruluş bağlamınızı yönetin.</h1>
        <p className="workspace-lead">
          Üyesi olduğunuz kuruluşları görüntüleyin, aktif bağlamı seçin ve
          üyeleri sunucu doğrulamasıyla inceleyin.
        </p>

        {workspace.selectionNotice ? (
          <p className="form-notice workspace-notice" role="status">
            {workspace.selectionNotice}
          </p>
        ) : null}
        {creationNotice ? (
          <p className="success-notice workspace-notice" role="status">
            {creationNotice}
          </p>
        ) : null}

        {workspace.membershipsPending ? (
          <section className="workspace-panel workspace-state" role="status">
            <span className="loading-line" aria-hidden="true" />
            <h2>Kuruluşlar yükleniyor</h2>
            <p>Üyelikleriniz güvenli çalışma alanı için hazırlanıyor.</p>
          </section>
        ) : null}

        {workspace.membershipsError ? (
          <section className="workspace-panel workspace-state" role="alert">
            <h2>Kuruluşlar alınamadı</h2>
            <p>{getOrganizationErrorMessage(workspace.membershipsError)}</p>
            <button
              className="secondary-button"
              type="button"
              onClick={workspace.refetchMemberships}
              disabled={workspace.membershipsFetching}
            >
              {workspace.membershipsFetching
                ? "Yeniden deneniyor…"
                : "Yeniden dene"}
            </button>
          </section>
        ) : null}

        {!workspace.membershipsPending &&
        !workspace.membershipsError &&
        workspace.memberships.length === 0 ? (
          <section className="workspace-panel empty-entity-state">
            <span className="section-kicker">İlk adım</span>
            <h2>Henüz bir kuruluşunuz yok.</h2>
            <p>
              Çalışma alanını kullanmak için ilk kuruluşunuzu oluşturun.
              Oluşturan hesap otomatik olarak yönetici üye olur.
            </p>
            <a className="primary-link-button" href="#create-legal-entity">
              Kuruluş oluştur
            </a>
          </section>
        ) : null}

        {!workspace.membershipsPending &&
        !workspace.membershipsError &&
        workspace.memberships.length > 0 ? (
          <div className="workspace-grid">
            <MembershipList
              memberships={workspace.memberships}
              selectedLegalEntityId={workspace.selectedLegalEntityId}
              onSelect={workspace.selectLegalEntity}
            />
            {workspace.selectedMembership ? (
              <SelectedEntityPanel
                selectedMembership={workspace.selectedMembership}
                detailQuery={detailQuery}
                membersQuery={membersQuery}
              />
            ) : (
              <section className="workspace-panel workspace-state">
                <h2>Aktif kuruluş seçin</h2>
                <p>
                  Detayları ve üye listesini görüntülemek için listeden veya üst
                  menüden bir kuruluş seçin.
                </p>
              </section>
            )}
          </div>
        ) : null}

        <CreateLegalEntityForm
          isPending={createMutation.isPending}
          error={createMutation.error}
          onSubmit={submitCreateEntity}
        />
      </div>
    </main>
  );
}
