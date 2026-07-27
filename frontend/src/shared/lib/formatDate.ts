/**
 * Shared date formatting. Consolidates nine near-identical `Intl.DateTimeFormat`
 * declarations that were copy-pasted across panels and pages.
 *
 * Three option sets are genuinely in use and must all survive — collapsing them
 * to one default would visibly change output:
 *   - "long-short"     dateStyle long + timeStyle short   (7 files)
 *   - "medium-short"   dateStyle medium + timeStyle short (3 usages)
 *   - "long-date-only" dateStyle long, no time            (extraction DATE rule)
 */

export type DateStyleVariant = "long-short" | "medium-short" | "long-date-only";

const FORMATTERS: Record<DateStyleVariant, Intl.DateTimeFormat> = {
  "long-short": new Intl.DateTimeFormat("tr-TR", {
    dateStyle: "long",
    timeStyle: "short",
  }),
  "medium-short": new Intl.DateTimeFormat("tr-TR", {
    dateStyle: "medium",
    timeStyle: "short",
  }),
  "long-date-only": new Intl.DateTimeFormat("tr-TR", { dateStyle: "long" }),
};

/** Formats a full ISO-8601 timestamp. Throws on invalid input, as before. */
export function formatDate(
  value: string,
  variant: DateStyleVariant = "long-short",
): string {
  return FORMATTERS[variant].format(new Date(value));
}

/** Nullable variant for optional timestamps such as requestedAt/completedAt. */
export function formatDateOrUndefined(
  value: string | null | undefined,
  variant: DateStyleVariant = "medium-short",
): string | undefined {
  return value ? formatDate(value, variant) : undefined;
}

/**
 * Formats a date-only `YYYY-MM-DD` value. Anchored to UTC midnight on purpose:
 * handing a bare date to `new Date()` would let a negative local UTC offset
 * shift the rendered calendar day backwards.
 */
export function formatDateOnly(isoDate: string): string {
  return FORMATTERS["long-date-only"].format(new Date(`${isoDate}T00:00:00Z`));
}
