import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";

import { logout } from "./authApi";
import { CURRENT_USER_QUERY_KEY } from "./useCurrentUser";
import { clearActiveSelectionUser } from "../organization/legalEntitySelection";

export function useLogout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  async function clearVerifiedSession() {
    clearActiveSelectionUser();
    navigate("/login", { replace: true, state: { reason: "logged-out" } });
    await queryClient.cancelQueries();
    queryClient.removeQueries({ queryKey: ["organization"] });
    queryClient.removeQueries({ queryKey: ["deals"] });
    queryClient.removeQueries({ queryKey: ["deal-invitations"] });
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null);
  }

  return useMutation({
    mutationFn: logout,
    onMutate: async () => {
      await queryClient.cancelQueries();
    },
    onSuccess: clearVerifiedSession,
  });
}
