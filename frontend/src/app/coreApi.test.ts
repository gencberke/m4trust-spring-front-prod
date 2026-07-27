import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  AUTH_SESSION_EXPIRED_EVENT,
  ApiError,
  patchJsonWithFreshCsrf,
  postJsonWithFreshCsrf,
  postNoContentWithFreshCsrf,
  requestJson,
} from "./coreApi";

const LEGAL_ENTITY_HEADER = "X-M4Trust-Legal-Entity-Id";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function problem(code: string, status = 409) {
  return {
    type: "https://m4trust.example/problems/test",
    title: "Test problem",
    status,
    detail: "Something went wrong.",
    code,
    correlationId: "corr-1",
  };
}

/** Captured (url, init) pairs for every fetch the code under test issued. */
let calls: Array<[string, RequestInit]>;

beforeEach(() => {
  calls = [];
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

describe("requestJson", () => {
  it("prefixes the API root and sends same-origin credentials", async () => {
    stubFetch(() => jsonResponse({ ok: true }));

    await expect(requestJson<{ ok: boolean }>("/deals")).resolves.toEqual({
      ok: true,
    });

    const [url, init] = calls[0]!;
    expect(url).toBe("/api/v1/deals");
    expect(init.credentials).toBe("same-origin");
    expect(new Headers(init.headers).get("Accept")).toBe(
      "application/json, application/problem+json",
    );
  });

  it("throws ApiError carrying the status and parsed problem detail", async () => {
    stubFetch(() => jsonResponse(problem("DEAL_VERSION_CONFLICT"), 409));

    const error = await requestJson("/deals").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).code).toBe("DEAL_VERSION_CONFLICT");
  });

  it("still throws ApiError when the error body is not a problem detail", async () => {
    stubFetch(
      () => new Response("<html>gateway error</html>", { status: 502 }),
    );

    const error = await requestJson("/deals").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).problem).toBeUndefined();
    expect((error as ApiError).code).toBeUndefined();
  });

  it("dispatches the session-expired event exactly once per expiry", async () => {
    stubFetch(() => jsonResponse(problem("AUTH_SESSION_EXPIRED", 401), 401));
    const listener = vi.fn();
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);

    await requestJson("/deals").catch(() => undefined);

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);
  });

  it("does not dispatch the session-expired event for other error codes", async () => {
    stubFetch(() => jsonResponse(problem("DEAL_NOT_FOUND", 404), 404));
    const listener = vi.fn();
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);

    await requestJson("/deals").catch(() => undefined);

    expect(listener).not.toHaveBeenCalled();
    window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, listener);
  });

  it("omits the legal-entity header when the context is suppressed", async () => {
    stubFetch(() => jsonResponse({}));

    await requestJson("/session", { suppressLegalEntityContext: true });

    expect(new Headers(calls[0]![1].headers).has(LEGAL_ENTITY_HEADER)).toBe(
      false,
    );
  });

  it("never forwards suppressLegalEntityContext into the fetch init", async () => {
    stubFetch(() => jsonResponse({}));

    await requestJson("/session", { suppressLegalEntityContext: true });

    expect(calls[0]![1]).not.toHaveProperty("suppressLegalEntityContext");
  });

  it("keeps an explicitly supplied legal-entity header", async () => {
    stubFetch(() => jsonResponse({}));

    await requestJson("/deals", {
      headers: { [LEGAL_ENTITY_HEADER]: "entity-explicit" },
    });

    expect(new Headers(calls[0]![1].headers).get(LEGAL_ENTITY_HEADER)).toBe(
      "entity-explicit",
    );
  });
});

describe("CSRF-protected writes", () => {
  function stubCsrfThen(responder: (url: string) => Response) {
    stubFetch((url) =>
      url.endsWith("/security/csrf")
        ? jsonResponse({ headerName: "X-CSRF-TOKEN", token: "token-1" })
        : responder(url),
    );
  }

  it("fetches a fresh token before every POST and sends it back", async () => {
    stubCsrfThen(() => jsonResponse({ id: "deal-1" }));

    await postJsonWithFreshCsrf("/deals", { title: "T" });

    expect(calls.map(([url]) => url)).toEqual([
      "/api/v1/security/csrf",
      "/api/v1/deals",
    ]);

    const [, writeInit] = calls[1]!;
    const headers = new Headers(writeInit.headers);
    expect(writeInit.method).toBe("POST");
    expect(headers.get("X-CSRF-TOKEN")).toBe("token-1");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(writeInit.body).toBe(JSON.stringify({ title: "T" }));
  });

  it("requests the token with cache: no-store so a rotated session is not reused", async () => {
    stubCsrfThen(() => jsonResponse({}));

    await postJsonWithFreshCsrf("/deals", {});

    expect(calls[0]![1].cache).toBe("no-store");
  });

  it("omits Content-Type when there is no body", async () => {
    stubCsrfThen(() => new Response(null, { status: 204 }));

    await postNoContentWithFreshCsrf("/deals/1/cancel");

    const headers = new Headers(calls[1]![1].headers);
    expect(headers.has("Content-Type")).toBe(false);
    expect(headers.get("X-CSRF-TOKEN")).toBe("token-1");
  });

  it("propagates a failed token fetch instead of issuing the write", async () => {
    stubFetch((url) =>
      url.endsWith("/security/csrf")
        ? jsonResponse(problem("AUTH_SESSION_EXPIRED", 401), 401)
        : jsonResponse({}),
    );

    await expect(postJsonWithFreshCsrf("/deals", {})).rejects.toBeInstanceOf(
      ApiError,
    );
    expect(calls).toHaveLength(1);
  });

  it("sends the token on PATCH as well", async () => {
    stubCsrfThen(() => jsonResponse({ id: "deal-1" }));

    await patchJsonWithFreshCsrf("/deals/1", { title: "T" });

    const [, writeInit] = calls[1]!;
    expect(writeInit.method).toBe("PATCH");
    expect(new Headers(writeInit.headers).get("X-CSRF-TOKEN")).toBe("token-1");
  });
});
