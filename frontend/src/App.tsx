import { Navigate, Route, Routes } from "react-router";

import {
  AnonymousOnlyRoute,
  HomeRedirect,
  ProtectedRoute,
} from "./features/auth/AuthRouteGuards";
import { AuthenticatedAppPage } from "./pages/AuthenticatedAppPage";
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
        <Route path="/app" element={<AuthenticatedAppPage />} />
      </Route>
      {import.meta.env.DEV ? (
        <Route path="/status" element={<PlatformStatusPage />} />
      ) : null}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
