import { useCallback, useEffect, useState } from "react";
import { NavLink, Outlet, useOutletContext } from "react-router";
import { useQuery } from "@tanstack/react-query";
import styles from "./AuthenticatedLayout.module.css";

import {
  getAuthErrorMessage,
  type CurrentUser,
  useLogout,
} from "../features/auth";
import {
  clearSelectedLegalEntityId,
  legalEntityMembershipsQueryOptions,
  type LegalEntityMembership,
  readSelectedLegalEntityId,
  saveSelectedLegalEntityId,
} from "../features/organization";

export interface AuthenticatedWorkspaceContext {
  user: CurrentUser;
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
    <label className={styles.entitySwitcher}>
      <span>Aktif kuruluş</span>
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

export function AuthenticatedLayout() {
  const user = useOutletContext<CurrentUser>();
  const [selectedLegalEntityId, setSelectedLegalEntityId] = useState<
    string | undefined
  >(readSelectedLegalEntityId);
  const [selectionNotice, setSelectionNotice] = useState<string>();
  const membershipsQuery = useQuery(legalEntityMembershipsQueryOptions());
  const memberships = membershipsQuery.data?.items ?? [];
  const selectedMembership = memberships.find(
    (membership) => membership.legalEntityId === selectedLegalEntityId,
  );
  const missingSelectedMembership =
    membershipsQuery.isSuccess &&
    Boolean(selectedLegalEntityId) &&
    !selectedMembership;
  const activeLegalEntityId = missingSelectedMembership
    ? undefined
    : selectedLegalEntityId;
  const activeSelectionNotice = missingSelectedMembership
    ? "Önceki kuruluş seçiminiz artık üyelikleriniz arasında değil ve temizlendi."
    : selectionNotice;

  const clearInvalidSelection = useCallback(() => {
    clearSelectedLegalEntityId();
    setSelectedLegalEntityId(undefined);
    setSelectionNotice(
      "Seçili kuruluşa erişiminiz artık yok. Lütfen yeniden seçim yapın.",
    );
  }, []);

  useEffect(() => {
    if (!missingSelectedMembership) {
      return;
    }
    clearSelectedLegalEntityId();
  }, [missingSelectedMembership]);

  function selectLegalEntity(legalEntityId: string | undefined) {
    setSelectionNotice(undefined);
    setSelectedLegalEntityId(legalEntityId);
    if (legalEntityId) {
      saveSelectedLegalEntityId(legalEntityId);
    } else {
      clearSelectedLegalEntityId();
    }
  }

  const logoutMutation = useLogout();

  const context: AuthenticatedWorkspaceContext = {
    user,
    memberships,
    membershipsPending: membershipsQuery.isPending,
    membershipsError: membershipsQuery.error,
    membershipsFetching: membershipsQuery.isFetching,
    selectedLegalEntityId: activeLegalEntityId,
    selectedMembership,
    selectionNotice: activeSelectionNotice,
    selectLegalEntity,
    clearInvalidSelection,
    refetchMemberships: () => {
      void membershipsQuery.refetch();
    },
  };

  return (
    <div className={`app-shell ${styles.authenticatedShell}`}>
      <header
        className={`site-header ${styles.authenticatedHeader} ${styles.workspaceHeader}`}
      >
        <NavLink
          className="brand brand-link"
          to="/app"
          aria-label="M4Trust ana çalışma alanı"
        >
          M4Trust
        </NavLink>
        <EntitySwitcher
          memberships={memberships}
          selectedLegalEntityId={activeLegalEntityId}
          disabled={membershipsQuery.isPending || memberships.length === 0}
          onChange={selectLegalEntity}
        />
        <div className={styles.accountActions}>
          <div className={styles.accountSummary} aria-label="Aktif hesap">
            <span>{user.displayName}</span>
            <span>{user.email}</span>
          </div>
          <button
            className={`text-button ${styles.logoutButton}`}
            type="button"
            onClick={() => logoutMutation.mutate()}
            disabled={logoutMutation.isPending}
          >
            {logoutMutation.isPending ? "Çıkılıyor…" : "Çıkış"}
          </button>
        </div>
        <nav className={styles.workspaceNav} aria-label="Çalışma alanı">
          <NavLink to="/app/deals">Anlaşmalar</NavLink>
          <NavLink to="/app/invitations">Davetler</NavLink>
        </nav>
      </header>

      {logoutMutation.isError ? (
        <div className={styles.layoutAlert}>
          <p className="form-alert" role="alert">
            {getAuthErrorMessage(logoutMutation.error, "logout")}
          </p>
        </div>
      ) : null}

      <Outlet context={context} />

      <footer className="site-footer">
        <p>
          Aktif kuruluş seçiminiz için tüm yetkiler sunucu tarafından
          doğrulanır.
        </p>
      </footer>
    </div>
  );
}
