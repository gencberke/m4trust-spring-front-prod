import { useCallback, useEffect, useState } from "react";
import {
  NavLink,
  Outlet,
  useNavigate,
  useOutletContext,
} from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { logout, type CurrentUser } from "../features/auth/authApi";
import { getAuthErrorMessage } from "../features/auth/authErrors";
import { CURRENT_USER_QUERY_KEY } from "../features/auth/useCurrentUser";
import type { LegalEntityMembership } from "../features/organization/organizationApi";
import { legalEntityMembershipsQueryOptions } from "../features/organization/organizationQueries";
import {
  clearActiveSelectionUser,
  clearSelectedLegalEntityId,
  readSelectedLegalEntityId,
  saveSelectedLegalEntityId,
} from "../features/organization/legalEntitySelection";

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

export function AuthenticatedLayout() {
  const user = useOutletContext<CurrentUser>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
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

  const clearInvalidSelection = useCallback(() => {
    clearSelectedLegalEntityId();
    setSelectedLegalEntityId(undefined);
    setSelectionNotice(
      "Seçili legal entity bulunamadı veya erişiminiz kaldırıldı. Lütfen yeniden seçim yapın.",
    );
  }, []);

  useEffect(() => {
    if (!missingSelectedMembership) {
      return;
    }
    clearSelectedLegalEntityId();
    setSelectedLegalEntityId(undefined);
    setSelectionNotice(
      "Önceki legal entity seçiminiz artık üyelikleriniz arasında değil ve temizlendi.",
    );
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

  async function clearVerifiedSession() {
    clearActiveSelectionUser();
    await queryClient.cancelQueries();
    queryClient.removeQueries({ queryKey: ["organization"] });
    queryClient.removeQueries({ queryKey: ["deals"] });
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null);
    navigate("/login", { replace: true, state: { reason: "logged-out" } });
  }

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: clearVerifiedSession,
  });

  const context: AuthenticatedWorkspaceContext = {
    user,
    memberships,
    membershipsPending: membershipsQuery.isPending,
    membershipsError: membershipsQuery.error,
    membershipsFetching: membershipsQuery.isFetching,
    selectedLegalEntityId,
    selectedMembership,
    selectionNotice,
    selectLegalEntity,
    clearInvalidSelection,
    refetchMemberships: () => {
      void membershipsQuery.refetch();
    },
  };

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
        <nav className="workspace-nav" aria-label="Çalışma alanı">
          <NavLink end to="/app">
            Organizasyon
          </NavLink>
          <NavLink to="/app/deals">Deals</NavLink>
        </nav>
      </header>

      {logoutMutation.isError ? (
        <div className="layout-alert">
          <p className="form-alert" role="alert">
            {getAuthErrorMessage(logoutMutation.error, "logout")}
          </p>
        </div>
      ) : null}

      <Outlet context={context} />

      <footer className="site-footer">
        <p>
          Aktif legal entity seçimi yalnızca istemci bağlamıdır; tüm yetki Core
          API tarafından doğrulanır.
        </p>
      </footer>
    </div>
  );
}
