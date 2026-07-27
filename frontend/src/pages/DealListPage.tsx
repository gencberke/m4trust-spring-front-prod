import { useOutletContext } from "react-router";

import { DealListWorkspace } from "../features/deals";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";

export function DealListPage() {
  return (
    <DealListWorkspace {...useOutletContext<AuthenticatedWorkspaceContext>()} />
  );
}
