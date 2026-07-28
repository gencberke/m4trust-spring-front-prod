import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router";
import { afterEach, describe, expect, it } from "vitest";

import { AuthSessionExpiryHandler } from "./AuthSessionExpiryHandler";
import { ProtectedRoute } from "./AuthRouteGuards";
import { AUTH_SESSION_EXPIRED_EVENT, type CurrentUser } from "./authApi";
import { CURRENT_USER_QUERY_KEY } from "./useCurrentUser";
import {
  clearActiveSelectionUser,
  readSelectedLegalEntityId,
  setActiveSelectionUser,
} from "../organization";

const user: CurrentUser = {
  id: "00000000-0000-4000-8000-000000000001",
  email: "user@example.test",
  displayName: "Test User",
  memberships: [],
};

function renderBoundary(currentUser: CurrentUser | null, path = "/app") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(CURRENT_USER_QUERY_KEY, currentUser);

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthSessionExpiryHandler />
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/app" element={<Outlet />}>
              <Route index element={<p>Protected workspace</p>} />
            </Route>
          </Route>
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return queryClient;
}

afterEach(() => {
  clearActiveSelectionUser();
  sessionStorage.clear();
});

describe("authentication boundary", () => {
  it("renders a protected route for an authenticated current user", () => {
    renderBoundary(user);

    expect(screen.getByText("Protected workspace")).toBeInTheDocument();
  });

  it("redirects an anonymous visitor away from a protected route", () => {
    renderBoundary(null);

    expect(screen.getByText("Login page")).toBeInTheDocument();
  });

  it("clears cached identity and selection after session expiry", async () => {
    const queryClient = renderBoundary(user);
    setActiveSelectionUser(user.id);

    window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT));

    expect(await screen.findByText("Login page")).toBeInTheDocument();
    expect(queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeNull();
    expect(readSelectedLegalEntityId()).toBeUndefined();
  });
});
