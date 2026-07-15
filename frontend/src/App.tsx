import { Route, Routes } from "react-router";

import { PlatformStatusPage } from "./pages/PlatformStatusPage";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<PlatformStatusPage />} />
    </Routes>
  );
}
