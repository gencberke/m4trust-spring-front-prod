import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ command, mode }) => {
  if (command !== "serve") {
    return {
      plugins: [react()],
    };
  }

  const environment = loadEnv(mode, ".", "");
  const proxyTarget = environment.CORE_API_PROXY_TARGET?.trim();

  if (!proxyTarget) {
    throw new Error("CORE_API_PROXY_TARGET is required for the Vite development proxy.");
  }

  const coreApiProxy = {
    target: proxyTarget,
    changeOrigin: true,
  };

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": coreApiProxy,
        "/actuator": coreApiProxy,
      },
    },
  };
});
