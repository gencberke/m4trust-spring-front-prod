import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router";

import { App } from "./App";
import { queryClient } from "./app/queryClient";
import { AuthSessionExpiryHandler } from "./features/auth/AuthSessionExpiryHandler";
import "./styles.css";

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("M4Trust root element was not found.");
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthSessionExpiryHandler />
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
