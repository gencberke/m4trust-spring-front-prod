import { useOutletContext, useParams } from "react-router";

import { DealDetailWorkspace } from "../features/deals";
import type { AuthenticatedWorkspaceContext } from "./AuthenticatedLayout";

export function DealDetailPage() {
  const { dealId } = useParams();
  const workspaceContext = useOutletContext<AuthenticatedWorkspaceContext>();

  return <DealDetailWorkspace {...workspaceContext} dealId={dealId} />;
}
