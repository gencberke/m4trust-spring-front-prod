import { fileURLToPath } from "node:url";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const srcPath = fileURLToPath(new URL("./src", import.meta.url));

const shared = {
  plugins: [react()],
  resolve: {
    alias: {
      "@": srcPath,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
};

export default defineConfig(({ command, mode }) => {
  if (command !== "serve") {
    return shared;
  }

  const environment = loadEnv(mode, ".", "");
  const proxyTarget = environment.CORE_API_PROXY_TARGET?.trim();

  if (!proxyTarget) {
    throw new Error(
      "CORE_API_PROXY_TARGET is required for the Vite development proxy.",
    );
  }

  const coreApiProxy = {
    target: proxyTarget,
    changeOrigin: true,
  };

  return {
    ...shared,
    server: {
      proxy: {
        "/api": coreApiProxy,
        "/actuator": coreApiProxy,
      },
    },
  };
});
