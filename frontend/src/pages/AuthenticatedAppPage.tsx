import { useOutletContext } from "react-router";

import { OrganizationWorkspace } from "../features/organization";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";

export function AuthenticatedAppPage() {
  const workspace = useOutletContext<AuthenticatedWorkspaceContext>();

  return <OrganizationWorkspace workspace={workspace} />;
}
