/* global console, process */

import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

const temporaryDirectory = mkdtempSync(resolve(tmpdir(), "m4trust-openapi-"));
const generatedPath = resolve(temporaryDirectory, "core-api.d.ts");
const committedPath = resolve("src/generated/core-api.d.ts");

try {
  const result = spawnSync(
    process.execPath,
    [
      resolve("node_modules/openapi-typescript/bin/cli.js"),
      "../contracts/openapi/core-api-v1.yaml",
      "-o",
      generatedPath,
    ],
    { stdio: "inherit" },
  );
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
  if (
    readFileSync(generatedPath, "utf8") !== readFileSync(committedPath, "utf8")
  ) {
    console.error(
      "Generated OpenAPI types differ from src/generated/core-api.d.ts.",
    );
    process.exitCode = 1;
  }
} finally {
  rmSync(temporaryDirectory, { force: true, recursive: true });
}
