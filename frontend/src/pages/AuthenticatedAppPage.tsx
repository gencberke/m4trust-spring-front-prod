import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  useEffect,
  useState,
  type FormEvent,
} from "react";
import { useNavigate, useOutletContext } from "react-router";

import { logout, type CurrentUser } from "../features/auth/authApi";
import { getAuthErrorMessage } from "../features/auth/authErrors";
import { CURRENT_USER_QUERY_KEY } from "../features/auth/useCurrentUser";
import {
  getLegalEntityFieldErrors,
  getOrganizationErrorMessage,
  isInvalidLegalEntitySelection,
  type LegalEntityField,
} from "../features/organization/organizationErrors";
import {
  createLegalEntity,
  type CreateLegalEntityRequest,
  type LegalEntity,
  type LegalEntityMember,
  type LegalEntityMemberList,
  type LegalEntityMembership,
} from "../features/organization/organizationApi";
import {
  LEGAL_ENTITY_MEMBERSHIPS_QUERY_KEY,
  legalEntityDetailQueryOptions,
  legalEntityMembershipsQueryOptions,
  legalEntityMembersQueryOptions,
} from "../features/organization/organizationQueries";
import {
  clearSelectedLegalEntityId,
  readSelectedLegalEntityId,
  saveSelectedLegalEntityId,
} from "../features/organization/legalEntitySelection";

function roleLabel(role: LegalEntityMembership["role"]): string {
  return role === "ADMIN" ? "Yönetici" : "Üye";
}

interface EntitySwitcherProps {
  memberships: LegalEntityMembership[];
  selectedLegalEntityId: string | undefined;
  disabled: boolean;
  onChange: (legalEntityId: string | undefined) => void;
}

function EntitySwitcher({
  memberships,
  selectedLegalEntityId,
  disabled,
  onChange,
}: EntitySwitcherProps) {
  return (
    <label className="entity-switcher">
      <span>Aktif legal entity</span>
      <select
        value={selectedLegalEntityId ?? ""}
        onChange={(event) => onChange(event.target.value || undefined)}
        disabled={disabled}
      >
        <option value="">Seçim yapın</option>
        {memberships.map((membership) => (
          <option
            key={membership.legalEntityId}
            value={membership.legalEntityId}
          >
            {membership.legalName}
          </option>
        ))}
      </select>
    </label>
  );
}

interface MembershipListProps {
  memberships: LegalEntityMembership[];
  selectedLegalEntityId: string | undefined;
  onSelect: (legalEntityId: string) => void;
}

function MembershipList({
  memberships,
  selectedLegalEntityId,
  onSelect,
}: MembershipListProps) {
  return (
    <section className="workspace-panel" aria-labelledby="entities-title">
      <div className="panel-heading">
        <span className="section-kicker">Organizasyonlar</span>
        <h2 id="entities-title">Legal entity’lerim</h2>
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
              <span className="role-badge">{roleLabel(membership.role)}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

interface CreateEntityFormProps {
  isPending: boolean;
  error: unknown;
  onSubmit: (
    request: CreateLegalEntityRequest,
    form: HTMLFormElement,
  ) => void;
}

function CreateEntityForm({
  isPending,
  error,
  onSubmit,
}: CreateEntityFormProps) {
  const [clientErrors, setClientErrors] = useState<
    Partial<Record<LegalEntityField, string>>
  >({});
  const serverErrors = getLegalEntityFieldErrors(error);
  const fieldErrors = { ...serverErrors, ...clientErrors };

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const request: CreateLegalEntityRequest = {
      legalName: String(formData.get("legalName") ?? "").trim(),
      registrationNumber: String(
        formData.get("registrationNumber") ?? "",
      ).trim(),
    };
    const validationErrors: Partial<Record<LegalEntityField, string>> = {};

    if (!request.legalName) {
      validationErrors.legalName = "Legal entity adını girin.";
    }
    if (!request.registrationNumber) {
      validationErrors.registrationNumber = "Kayıt numarasını girin.";
    }
    setClientErrors(validationErrors);

    if (Object.keys(validationErrors).length === 0) {
      onSubmit(request, form);
    }
  }

  return (
    <section
      className="workspace-panel create-entity-panel"
      id="create-legal-entity"
      aria-labelledby="create-entity-title"
    >
      <div className="panel-heading">
        <span className="section-kicker">Yeni çalışma alanı</span>
        <h2 id="create-entity-title">Legal entity oluşturun</h2>
        <p>Resmî adı ve kurum kayıt numarasını girerek başlayın.</p>
      </div>

      {error ? (
        <p className="form-alert panel-alert" role="alert">
          {getOrganizationErrorMessage(error)}
        </p>
      ) : null}

      <form className="auth-form organization-form" onSubmit={handleSubmit}>
        <div className="field-group">
          <label htmlFor="legal-entity-name">Legal entity adı</label>
          <input
            id="legal-entity-name"
            name="legalName"
            type="text"
            required
            maxLength={200}
            aria-invalid={Boolean(fieldErrors.legalName)}
            aria-describedby={
              fieldErrors.legalName ? "legal-entity-name-error" : undefined
            }
            onChange={() =>
              setClientErrors((current) => ({
                ...current,
                legalName: undefined,
              }))
            }
          />
          {fieldErrors.legalName ? (
            <span className="field-error" id="legal-entity-name-error">
              {fieldErrors.legalName}
            </span>
          ) : null}
        </div>

        <div className="field-group">
          <label htmlFor="legal-entity-registration-number">Kayıt numarası</label>
          <input
            id="legal-entity-registration-number"
            name="registrationNumber"
            type="text"
            required
            maxLength={100}
            aria-invalid={Boolean(fieldErrors.registrationNumber)}
            aria-describedby={
              fieldErrors.registrationNumber
                ? "legal-entity-registration-number-error"
                : undefined
            }
            onChange={() =>
              setClientErrors((current) => ({
                ...current,
                registrationNumber: undefined,
              }))
            }
          />
          {fieldErrors.registrationNumber ? (
            <span
              className="field-error"
              id="legal-entity-registration-number-error"
            >
              {fieldErrors.registrationNumber}
            </span>
          ) : null}
        </div>

        <button className="primary-button" type="submit" disabled={isPending}>
          {isPending ? "Oluşturuluyor…" : "Legal entity oluştur"}
        </button>
      </form>
    </section>
  );
}

interface SelectedEntityPanelProps {
  selectedMembership: LegalEntityMembership;
  detailQuery: UseQueryResult<LegalEntity>;
  membersQuery: UseQueryResult<LegalEntityMemberList>;
}

function SelectedEntityPanel({
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
          Bu paneldeki istekler seçili legal entity kimliğiyle sunucuda yeniden
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
          <p>Legal entity bilgileri yükleniyor…</p>
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
            <dd>{roleLabel(selectedMembership.role)}</dd>
          </div>
        </dl>
      ) : null}

      {membersQuery.data ? (
        <div className="members-region">
          <h3>Üyeler</h3>
          {membersQuery.data.items.length === 0 ? (
            <p className="muted-copy">Bu legal entity için üye bulunamadı.</p>
          ) : (
            <ul className="member-list">
              {membersQuery.data.items.map((member: LegalEntityMember) => (
                <li key={member.userId}>
                  <span>
                    <strong>{member.displayName}</strong>
                    <small>{member.email}</small>
                  </span>
                  <span className="role-badge">{roleLabel(member.role)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </section>
  );
}

export function AuthenticatedAppPage() {
  const user = useOutletContext<CurrentUser>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedLegalEntityId, setSelectedLegalEntityId] = useState<
    string | undefined
  >(readSelectedLegalEntityId);
  const [selectionNotice, setSelectionNotice] = useState<string>();
  const [creationNotice, setCreationNotice] = useState<string>();

  const membershipsQuery = useQuery(legalEntityMembershipsQueryOptions());
  const memberships = membershipsQuery.data?.items ?? [];
  const selectedMembership = memberships.find(
    (membership) => membership.legalEntityId === selectedLegalEntityId,
  );
  const detailQuery = useQuery(
    legalEntityDetailQueryOptions(selectedMembership?.legalEntityId),
  );
  const membersQuery = useQuery(
    legalEntityMembersQueryOptions(selectedMembership?.legalEntityId),
  );
  const invalidScopedSelection =
    isInvalidLegalEntitySelection(detailQuery.error) ||
    isInvalidLegalEntitySelection(membersQuery.error);
  const missingSelectedMembership =
    membershipsQuery.isSuccess &&
    Boolean(selectedLegalEntityId) &&
    !selectedMembership;

  useEffect(() => {
    if (!invalidScopedSelection && !missingSelectedMembership) {
      return;
    }

    clearSelectedLegalEntityId();
    setSelectedLegalEntityId(undefined);
    setSelectionNotice(
      invalidScopedSelection
        ? "Seçili legal entity bulunamadı veya erişiminiz kaldırıldı. Lütfen yeniden seçim yapın."
        : "Önceki legal entity seçiminiz artık üyelikleriniz arasında değil ve temizlendi.",
    );
  }, [invalidScopedSelection, missingSelectedMembership]);

  function selectLegalEntity(legalEntityId: string | undefined) {
    setCreationNotice(undefined);
    setSelectionNotice(undefined);
    setSelectedLegalEntityId(legalEntityId);
    if (legalEntityId) {
      saveSelectedLegalEntityId(legalEntityId);
    } else {
      clearSelectedLegalEntityId();
    }
  }

  async function clearVerifiedSession() {
    clearSelectedLegalEntityId();
    await queryClient.cancelQueries();
    queryClient.removeQueries({ queryKey: ["organization"] });
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null);
    navigate("/login", { replace: true, state: { reason: "logged-out" } });
  }

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: clearVerifiedSession,
  });

  const createMutation = useMutation({
    mutationFn: createLegalEntity,
    onSuccess: async (createdEntity) => {
      setCreationNotice(
        `${createdEntity.legalName} oluşturuldu ve aktif legal entity olarak seçildi.`,
      );
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: LEGAL_ENTITY_MEMBERSHIPS_QUERY_KEY,
        }),
        queryClient.invalidateQueries({
          queryKey: CURRENT_USER_QUERY_KEY,
        }),
      ]);
      saveSelectedLegalEntityId(createdEntity.id);
      setSelectedLegalEntityId(createdEntity.id);
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
    <div className="app-shell authenticated-shell">
      <header className="site-header authenticated-header workspace-header">
        <span className="brand" aria-label="M4Trust">
          M4Trust
        </span>
        <EntitySwitcher
          memberships={memberships}
          selectedLegalEntityId={selectedLegalEntityId}
          disabled={membershipsQuery.isPending || memberships.length === 0}
          onChange={selectLegalEntity}
        />
        <div className="account-actions">
          <div className="account-summary" aria-label="Aktif hesap">
            <span>{user.displayName}</span>
            <span>{user.email}</span>
          </div>
          <button
            className="text-button"
            type="button"
            onClick={() => logoutMutation.mutate()}
            disabled={logoutMutation.isPending}
          >
            {logoutMutation.isPending ? "Çıkılıyor…" : "Çıkış"}
          </button>
        </div>
      </header>

      <main className="workspace-main organization-workspace">
        <div className="workspace-column">
          <span className="section-kicker">Organizasyon çalışma alanı</span>
          <h1>Legal entity bağlamınızı yönetin.</h1>
          <p className="workspace-lead">
            Üyesi olduğunuz legal entity’leri görüntüleyin, aktif bağlamı seçin
            ve üyeleri sunucu doğrulamasıyla inceleyin.
          </p>

          {selectionNotice ? (
            <p className="form-notice workspace-notice" role="status">
              {selectionNotice}
            </p>
          ) : null}
          {creationNotice ? (
            <p className="success-notice workspace-notice" role="status">
              {creationNotice}
            </p>
          ) : null}
          {logoutMutation.isError ? (
            <p className="form-alert workspace-alert" role="alert">
              {getAuthErrorMessage(logoutMutation.error, "logout")}
            </p>
          ) : null}

          {membershipsQuery.isPending ? (
            <section className="workspace-panel workspace-state" role="status">
              <span className="loading-line" aria-hidden="true" />
              <h2>Legal entity’ler yükleniyor</h2>
              <p>Üyelikleriniz güvenli çalışma alanı için hazırlanıyor.</p>
            </section>
          ) : null}

          {membershipsQuery.isError ? (
            <section className="workspace-panel workspace-state" role="alert">
              <h2>Legal entity’ler alınamadı</h2>
              <p>{getOrganizationErrorMessage(membershipsQuery.error)}</p>
              <button
                className="secondary-button"
                type="button"
                onClick={() => void membershipsQuery.refetch()}
                disabled={membershipsQuery.isFetching}
              >
                {membershipsQuery.isFetching ? "Yeniden deneniyor…" : "Yeniden dene"}
              </button>
            </section>
          ) : null}

          {membershipsQuery.isSuccess && memberships.length === 0 ? (
            <section className="workspace-panel empty-entity-state">
              <span className="section-kicker">İlk adım</span>
              <h2>Henüz bir legal entity’niz yok.</h2>
              <p>
                Çalışma alanını kullanmak için ilk legal entity’nizi oluşturun.
                Oluşturan hesap otomatik olarak yönetici üye olur.
              </p>
              <a className="primary-link-button" href="#create-legal-entity">
                Legal entity oluştur
              </a>
            </section>
          ) : null}

          {membershipsQuery.isSuccess && memberships.length > 0 ? (
            <div className="workspace-grid">
              <MembershipList
                memberships={memberships}
                selectedLegalEntityId={selectedLegalEntityId}
                onSelect={selectLegalEntity}
              />
              {selectedMembership ? (
                <SelectedEntityPanel
                  selectedMembership={selectedMembership}
                  detailQuery={detailQuery}
                  membersQuery={membersQuery}
                />
              ) : (
                <section className="workspace-panel workspace-state">
                  <h2>Aktif legal entity seçin</h2>
                  <p>
                    Detayları ve üye listesini görüntülemek için listeden veya
                    üst menüden bir legal entity seçin.
                  </p>
                </section>
              )}
            </div>
          ) : null}

          <CreateEntityForm
            isPending={createMutation.isPending}
            error={createMutation.error}
            onSubmit={submitCreateEntity}
          />
        </div>
      </main>

      <footer className="site-footer">
        <p>
          Aktif legal entity seçimi yalnızca istemci bağlamıdır; tüm yetki Core
          API tarafından doğrulanır.
        </p>
      </footer>
    </div>
  );
}
