import {
  queryOptions,
  type QueryClient,
  useQuery,
} from "@tanstack/react-query";

import { fetchCurrentUser } from "./authApi";

export const CURRENT_USER_QUERY_KEY = ["auth", "current-user"] as const;

export function currentUserQueryOptions() {
  return queryOptions({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: ({ signal }) => fetchCurrentUser(signal),
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: "always" as const,
  });
}

export async function refreshCurrentUserAfterAuthentication(
  queryClient: QueryClient,
) {
  await queryClient.cancelQueries({
    queryKey: CURRENT_USER_QUERY_KEY,
    exact: true,
  });
  await queryClient.invalidateQueries({
    queryKey: CURRENT_USER_QUERY_KEY,
    exact: true,
    refetchType: "none",
  });
  const currentUser = await queryClient.fetchQuery(currentUserQueryOptions());

  if (!currentUser) {
    throw new Error("Authenticated current-user bootstrap could not be restored.");
  }

  return currentUser;
}

export function useCurrentUser() {
  return useQuery(currentUserQueryOptions());
}
