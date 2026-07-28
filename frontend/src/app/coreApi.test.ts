import { afterEach, beforeEach, expect, it, vi } from "vitest";

import {
  AUTH_SESSION_EXPIRED_EVENT,
  ApiError,
  postJsonWithFreshCsrf,
  requestJson,
} from "./coreApi";
import { setActiveSelectionUser } from "../features/organization";

const LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";
const ENTITY_ID = "11111111-1111-4111-8111-111111111111";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function problem(code: string, status: number) {
  return {
    type: "https://m4trust.example/problems/test",
    title: "Test problem",
    status,
    detail: "Something went wrong.",
    code,
    correlationId: "corr-1",
  };
}

let calls: Array<[string, RequestInit]>;

beforeEach(() => {
  calls = [];
  sessionStorage.clear();
  setActiveSelectionUser("00000000-0000-4000-8000-000000000001");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function stubFetch(handler: (url: string, init: RequestInit) => Response) {
  vi.stubGlobal(
    "fetch",
    vi.fn((input: RequestInfo | URL, init: RequestInit = {}) => {
      const url = String(input);
      calls.push([url, init]);
      return Promise.resolve(handler(url, init));
    }),
  );
}

it("uses same-origin Core requests and forwards selected legal-entity context", async () => {
  sessionStorage.setItem(
    "m4trust:selected-legal-entity-id:v2:00000000-0000-4000-8000-000000000001",
    ENTITY_ID,
  );
  setActiveSelectionUser("00000000-0000-4000-8000-000000000002");
  setActiveSelectionUser("00000000-0000-4000-8000-000000000001");
  stubFetch(() => jsonResponse({ ok: true }));

  await expect(requestJson<{ ok: boolean }>("/deals")).resolves.toEqual({
    ok: true,
  });

  const [url, init] = calls[0]!;
  expect(url).toBe("/api/v1/deals");
  expect(init.credentials).toBe("same-origin");
  expect(new Headers(init.headers).get(LEGAL_ENTITY_HEADER)).toBe(ENTITY_ID);
});

it("turns a session-expired Problem Detail into the application expiry event", async () => {
  stubFetch(() => jsonResponse(problem("AUTH_SESSION_EXPIRED", 401), 401));
  const listener = vi.fn();
  window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);

  const error = await requestJson("/deals").catch((reason: unknown) => reason);

  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(401);
  expect(listener).toHaveBeenCalledTimes(1);
  window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);
});

it("obtains a fresh no-store CSRF token before an unsafe JSON write", async () => {
  stubFetch((url) =>
    url.endsWith("/security/csrf")
      ? jsonResponse({ headerName: "X-CSRF-TOKEN", token: "token-1" })
      : jsonResponse({ id: "deal-1" }),
  );

  await postJsonWithFreshCsrf("/deals", { title: "T" });

  expect(calls.map(([url]) => url)).toEqual([
    "/api/v1/security/csrf",
    "/api/v1/deals",
  ]);
  expect(calls[0]![1].cache).toBe("no-store");
  const headers = new Headers(calls[1]![1].headers);
  expect(headers.get("X-CSRF-TOKEN")).toBe("token-1");
  expect(headers.get("Content-Type")).toBe("application/json");
});
