import { Navigate, Outlet, useLocation } from "react-router";

import { AuthQueryState } from "./AuthQueryState";
import { useCurrentUser } from "./useCurrentUser";

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
