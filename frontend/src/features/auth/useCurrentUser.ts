import { useQuery } from "@tanstack/react-query";

import { fetchCurrentUser } from "./authApi";

export const CURRENT_USER_QUERY_KEY = ["auth", "current-user"] as const;

export function useCurrentUser() {
  return useQuery({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: ({ signal }) => fetchCurrentUser(signal),
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: "always",
  });
}
