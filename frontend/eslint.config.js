import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["dist", "src/generated"],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],

      // Underscore prefix marks a deliberately unused binding.
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
          varsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
        },
      ],

      // Phase 3 removed the synchronous state mirroring that previously made
      // this rule advisory. Keep the regression gate strict from here on.
      "react-hooks/set-state-in-effect": "error",

      // Feature isolation: a feature may only be
      // reached through its public barrel.
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["**/features/*/*"],
              message:
                "Import a feature through its index.ts barrel, not its internal files.",
            },
          ],
        },
      ],
    },
  },
  {
    // Relative sibling-feature deep imports (for example ../organization/foo) bypass
    // the **/features/*/* pattern because "features" is not in the path string.
    // Coverage is tied to directory depth: files under features/x/* use ../,
    // features/x/components/* use ../../. A file at features/x/components/sub/
    // reaching a sibling via ../../../ is not matched today (no such path exists yet).
    files: ["src/features/*/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["**/features/*/*"],
              message:
                "Import a feature through its index.ts barrel, not its internal files.",
            },
            {
              regex: "^\\.\\./[^./][^/]*/.+",
              message:
                "Import a feature through its index.ts barrel, not its internal files.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["src/features/*/*/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["**/features/*/*"],
              message:
                "Import a feature through its index.ts barrel, not its internal files.",
            },
            {
              regex: "^\\.\\./\\.\\./[^./][^/]*/.+",
              message:
                "Import a feature through its index.ts barrel, not its internal files.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["**/*.test.{ts,tsx}", "src/test/**"],
    rules: {
      "no-restricted-imports": "off",
      "@typescript-eslint/no-explicit-any": "off",
    },
  },
);
