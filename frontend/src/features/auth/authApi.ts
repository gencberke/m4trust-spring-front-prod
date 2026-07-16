import type { components } from "../../generated/core-api";

export type LoginRequest = components["schemas"]["LoginRequest"];
export type ProblemDetail = components["schemas"]["ProblemDetail"];
export type PublicUser = components["schemas"]["PublicUser"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];

type CsrfToken = components["schemas"]["CsrfToken"];

const API_ROOT = "/api/v1";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem?: ProblemDetail,
  ) {
    super("The API request could not be completed.");
    this.name = "ApiError";
  }

  get code() {
    return this.problem?.code;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return (
    isRecord(value) &&
    typeof value.type === "string" &&
    typeof value.title === "string" &&
    typeof value.status === "number" &&
    typeof value.detail === "string" &&
    typeof value.code === "string" &&
    typeof value.correlationId === "string"
  );
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  const payload: unknown = await response.json().catch(() => undefined);
  return isProblemDetail(payload) ? payload : undefined;
}

async function requestJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...init,
    credentials: "same-origin",
    headers: {
      Accept: "application/json, application/problem+json",
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  return (await response.json()) as T;
}

async function fetchCsrfToken(signal?: AbortSignal): Promise<CsrfToken> {
  return requestJson<CsrfToken>("/security/csrf", {
    cache: "no-store",
    signal,
  });
}

function postWithFreshCsrf<T>(
  path: string,
  body: LoginRequest | RegisterRequest,
): Promise<T>;
function postWithFreshCsrf(path: string): Promise<void>;
async function postWithFreshCsrf(
  path: string,
  body?: LoginRequest | RegisterRequest,
): Promise<unknown> {
  // This dependency is intentional: session rotation invalidates earlier CSRF tokens.
  const csrf = await fetchCsrfToken();
  const response = await fetch(`${API_ROOT}${path}`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json, application/problem+json",
      ...(body ? { "Content-Type": "application/json" } : {}),
      [csrf.headerName]: csrf.token,
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  if (response.status === 204) {
    return undefined;
  }

  return response.json();
}

export async function fetchCurrentUser(signal?: AbortSignal): Promise<PublicUser | null> {
  try {
    return await requestJson<PublicUser>("/auth/me", { signal });
  } catch (error) {
    if (error instanceof ApiError && error.code === "AUTH_SESSION_EXPIRED") {
      return null;
    }
    throw error;
  }
}

export function register(request: RegisterRequest): Promise<PublicUser> {
  return postWithFreshCsrf<PublicUser>("/auth/register", request);
}

export function login(request: LoginRequest): Promise<PublicUser> {
  return postWithFreshCsrf<PublicUser>("/auth/login", request);
}

export function logout(): Promise<void> {
  return postWithFreshCsrf("/auth/logout");
}
