import type { components } from "../../generated/core-api";
import {
  ApiError,
  AUTH_SESSION_EXPIRED_EVENT,
  postJsonWithFreshCsrf,
  postNoContentWithFreshCsrf,
  requestJson,
  type ProblemDetail,
} from "../../app/coreApi";

export type CurrentUser = components["schemas"]["CurrentUser"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type PublicUser = components["schemas"]["PublicUser"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];

export { ApiError, AUTH_SESSION_EXPIRED_EVENT };
export type { ProblemDetail };

export async function fetchCurrentUser(signal?: AbortSignal): Promise<CurrentUser | null> {
  try {
    return await requestJson<CurrentUser>("/auth/me", { signal });
  } catch (error) {
    if (error instanceof ApiError && error.code === "AUTH_SESSION_EXPIRED") {
      return null;
    }
    throw error;
  }
}

export function register(request: RegisterRequest): Promise<PublicUser> {
  return postJsonWithFreshCsrf<PublicUser>("/auth/register", request);
}

export function login(request: LoginRequest): Promise<PublicUser> {
  return postJsonWithFreshCsrf<PublicUser>("/auth/login", request);
}

export function logout(): Promise<void> {
  return postNoContentWithFreshCsrf("/auth/logout");
}
