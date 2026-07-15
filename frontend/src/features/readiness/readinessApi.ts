export interface ReadinessPayload {
  status: "UP";
}

function hasUpStatus(payload: unknown): payload is ReadinessPayload {
  return (
    typeof payload === "object" &&
    payload !== null &&
    "status" in payload &&
    payload.status === "UP"
  );
}

export async function fetchReadiness(signal: AbortSignal): Promise<ReadinessPayload> {
  const response = await fetch("/actuator/health/readiness", {
    signal,
    headers: {
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`Core API readiness request failed with status ${response.status}.`);
  }

  const payload: unknown = await response.json();

  if (!hasUpStatus(payload)) {
    throw new Error("Core API readiness response did not report UP.");
  }

  return payload;
}
