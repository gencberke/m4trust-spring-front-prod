import { useOutletContext } from "react-router";

import { IncomingInvitationsWorkspace } from "../features/invitations";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";

export function IncomingInvitationsPage() {
  const { user, selectLegalEntity } =
    useOutletContext<AuthenticatedWorkspaceContext>();

  return (
    <IncomingInvitationsWorkspace
      memberships={user.memberships}
      selectLegalEntity={selectLegalEntity}
    />
  );
}
