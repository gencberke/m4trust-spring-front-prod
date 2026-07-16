import type { components } from "../generated/core-api";
import { readSelectedLegalEntityId } from "../features/organization/legalEntitySelection";

export type ProblemDetail = components["schemas"]["ProblemDetail"];

type CsrfToken = components["schemas"]["CsrfToken"];

const API_ROOT = "/api/v1";

export const AUTH_SESSION_EXPIRED_EVENT = "m4trust:auth-session-expired";

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

async function apiError(response: Response): Promise<ApiError> {
  const error = new ApiError(response.status, await readProblem(response));
  if (error.code === "AUTH_SESSION_EXPIRED") {
    window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT));
  }
  return error;
}

function buildHeaders(initHeaders?: HeadersInit): Headers {
  const headers = new Headers(initHeaders);
  headers.set("Accept", "application/json, application/problem+json");

  if (!headers.has("X-M4Trust-Legal-Entity-Id")) {
    const selectedLegalEntityId = readSelectedLegalEntityId();
    if (selectedLegalEntityId) {
      headers.set("X-M4Trust-Legal-Entity-Id", selectedLegalEntityId);
    }
  }

  return headers;
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...init,
    credentials: "same-origin",
    headers: buildHeaders(init.headers),
  });

  if (!response.ok) {
    throw await apiError(response);
  }

  return response;
}

export async function requestJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await request(path, init);
  return (await response.json()) as T;
}

async function fetchCsrfToken(signal?: AbortSignal): Promise<CsrfToken> {
  return requestJson<CsrfToken>("/security/csrf", {
    cache: "no-store",
    signal,
  });
}

async function postWithFreshCsrf(
  path: string,
  body?: unknown,
  initHeaders?: HeadersInit,
): Promise<Response> {
  // This dependency is intentional: session rotation invalidates earlier CSRF tokens.
  const csrf = await fetchCsrfToken();
  const headers = buildHeaders(initHeaders);
  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  headers.set(csrf.headerName, csrf.token);

  return request(path, {
    method: "POST",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function postJsonWithFreshCsrf<T>(
  path: string,
  body: unknown,
  headers?: HeadersInit,
): Promise<T> {
  const response = await postWithFreshCsrf(path, body, headers);
  return (await response.json()) as T;
}

export async function postNoContentWithFreshCsrf(path: string): Promise<void> {
  await postWithFreshCsrf(path);
}

export async function postJsonWithoutBodyWithFreshCsrf<T>(
  path: string,
  headers?: HeadersInit,
): Promise<T> {
  const response = await postWithFreshCsrf(path, undefined, headers);
  return (await response.json()) as T;
}

export async function patchJsonWithFreshCsrf<T>(
  path: string,
  body: unknown,
  initHeaders?: HeadersInit,
): Promise<T> {
  const csrf = await fetchCsrfToken();
  const headers = buildHeaders(initHeaders);
  headers.set("Content-Type", "application/json");
  headers.set(csrf.headerName, csrf.token);

  const response = await request(path, {
    method: "PATCH",
    headers,
    body: JSON.stringify(body),
  });
  return (await response.json()) as T;
}
