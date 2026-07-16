import { Navigate, Outlet, useLocation } from "react-router";

import { AuthQueryState } from "./AuthQueryState";
import { useCurrentUser } from "./useCurrentUser";
import {
  clearActiveSelectionUser,
  setActiveSelectionUser,
} from "../organization/legalEntitySelection";

export function HomeRedirect() {
  const currentUser = useCurrentUser();

  if (currentUser.isPending || currentUser.isError) {
    return <AuthQueryState query={currentUser} />;
  }

  return <Navigate to={currentUser.data ? "/app" : "/login"} replace />;
}

export function ProtectedRoute() {
  const currentUser = useCurrentUser();
  const location = useLocation();

  // Synchronised during render (not in an effect) so the per-user selection
  // scope is active before any nested route reads the stored selection on
  // its own initial render (e.g. AuthenticatedLayout's lazy useState).
  if (currentUser.data?.id) {
    setActiveSelectionUser(currentUser.data.id);
  } else if (currentUser.data === null) {
    clearActiveSelectionUser();
  }

  if (currentUser.isPending || currentUser.isError) {
    return <AuthQueryState query={currentUser} />;
  }

  if (!currentUser.data) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ reason: "session-expired", from: location.pathname }}
      />
    );
  }

  return <Outlet context={currentUser.data} />;
}

export function AnonymousOnlyRoute() {
  const currentUser = useCurrentUser();

  if (currentUser.isPending || currentUser.isError) {
    return <AuthQueryState query={currentUser} />;
  }

  return currentUser.data ? <Navigate to="/app" replace /> : <Outlet />;
}
