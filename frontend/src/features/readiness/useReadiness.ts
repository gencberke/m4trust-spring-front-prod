import { useQuery } from "@tanstack/react-query";

import { fetchReadiness } from "./readinessApi";

const READINESS_QUERY_KEY = ["core-api", "readiness"] as const;

export function useReadiness() {
  return useQuery({
    queryKey: READINESS_QUERY_KEY,
    queryFn: ({ signal }) => fetchReadiness(signal),
  });
}
