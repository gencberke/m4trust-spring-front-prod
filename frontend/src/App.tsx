import { Navigate, Route, Routes } from "react-router";

import {
  AnonymousOnlyRoute,
  HomeRedirect,
  ProtectedRoute,
} from "./features/auth/AuthRouteGuards";
import { DealDetailPage } from "./pages/DealDetailPage";
import { DealListPage } from "./pages/DealListPage";
import { IncomingInvitationsPage } from "./pages/IncomingInvitationsPage";
import { AuthenticatedAppPage } from "./pages/AuthenticatedAppPage";
import { AuthenticatedLayout } from "./pages/AuthenticatedLayout";
import { LoginPage } from "./pages/LoginPage";
import { PlatformStatusPage } from "./pages/PlatformStatusPage";
import { RegisterPage } from "./pages/RegisterPage";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route element={<AnonymousOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AuthenticatedLayout />}>
          <Route index element={<AuthenticatedAppPage />} />
          <Route path="deals" element={<DealListPage />} />
          <Route path="deals/:dealId" element={<DealDetailPage />} />
          <Route path="invitations" element={<IncomingInvitationsPage />} />
        </Route>
      </Route>
      {import.meta.env.DEV ? (
        <Route path="/status" element={<PlatformStatusPage />} />
      ) : null}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
