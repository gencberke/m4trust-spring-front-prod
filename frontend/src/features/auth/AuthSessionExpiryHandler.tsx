import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate } from "react-router";

import { AUTH_SESSION_EXPIRED_EVENT } from "./authApi";
import { CURRENT_USER_QUERY_KEY } from "./useCurrentUser";

const ANONYMOUS_ROUTES = new Set(["/login", "/register"]);

export function AuthSessionExpiryHandler() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    function handleSessionExpiry() {
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null);

      if (!ANONYMOUS_ROUTES.has(location.pathname)) {
        navigate("/login", {
          replace: true,
          state: { reason: "session-expired", from: location.pathname },
        });
      }
    }

    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, handleSessionExpiry);
    return () => window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, handleSessionExpiry);
  }, [location.pathname, navigate, queryClient]);

  return null;
}
