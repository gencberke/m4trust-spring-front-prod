/**
 * Shared minor-unit <-> decimal money helpers. Amounts always travel the wire
 * as integer minor units; conversion uses BigInt so no binary float ever
 * touches a monetary value.
 *
 * Consolidated from the former `src/app/money.ts` and the private
 * reimplementations that had drifted inside DealReviewWorkspace and
 * DealContractAnalysis.
 */

/**
 * Converts an integer minor-unit amount to a plain decimal string
 * (`150050` -> `"1500.50"`). Locale-agnostic and without a currency symbol;
 * call sites append the currency code themselves.
 *
 * Sign-safe. The former `app/money.ts` version padded the raw `String(value)`,
 * so a value of `-5` became `"0-5"` and rendered as `"0.-5"`. Its callers only
 * ever passed positive amounts, so the bug was latent; the correct algorithm
 * from DealReviewWorkspace is used here and is a superset of both.
 */
export function decimalFromMinor(value: number): string {
  const sign = value < 0 ? "-" : "";
  const digits = String(Math.abs(value)).padStart(3, "0");
  return `${sign}${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/**
 * Converts decimal text to a scaled integer using BigInt; float never touches
 * the wire. `scale` is the number of fraction digits to preserve (2 for money,
 * 2 for basis points, 0 for whole seconds). Returns undefined when the text is
 * malformed, has too many fraction digits, or falls outside
 * `[minimum, Number.MAX_SAFE_INTEGER]`.
 */
export function decimalToInteger(
  text: string,
  scale: number,
  minimum = 0,
): number | undefined {
  const normalized = text.trim().replace(",", ".");
  if (!/^-?\d+(?:\.\d+)?$/.test(normalized)) return undefined;
  const negative = normalized.startsWith("-");
  const [wholeRaw, fractionRaw = ""] = (
    negative ? normalized.slice(1) : normalized
  ).split(".");
  if (fractionRaw.length > scale) return undefined;
  const integer = BigInt(
    `${negative ? "-" : ""}${wholeRaw}${fractionRaw.padEnd(scale, "0")}`,
  );
  if (integer < BigInt(minimum) || integer > BigInt(Number.MAX_SAFE_INTEGER)) {
    return undefined;
  }
  return Number(integer);
}

/**
 * Positive currency amount in minor units. Preserves the exact contract of the
 * former `app/money.ts#decimalToMinor`: scale 2, rejects zero and negatives.
 */
export function decimalToMinor(text: string): number | undefined {
  return decimalToInteger(text, 2, 1);
}

/** Parses decimal text into a plain finite number. Non-monetary (quantities). */
export function decimalToFiniteNumber(text: string): number | undefined {
  const normalized = text.trim().replace(",", ".");
  if (!/^-?\d+(?:\.\d+)?$/.test(normalized)) return undefined;
  const value = Number(normalized);
  return Number.isFinite(value) ? value : undefined;
}

const MONEY_FORMATTER_CACHE = new Map<string, Intl.NumberFormat>();

/**
 * Fully localized currency string (`"₺1.500,50"`). Deliberately distinct from
 * `decimalFromMinor`, which returns a bare decimal for call sites that render
 * the currency code as a separate literal — the two outputs differ and both are
 * in use.
 */
export function formatMoney(amountMinor: number, currency: string): string {
  let formatter = MONEY_FORMATTER_CACHE.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency,
    });
    MONEY_FORMATTER_CACHE.set(currency, formatter);
  }
  return formatter.format(amountMinor / 100);
}

/** Percentage formatter shared by contract analysis and video analysis. */
export const PERCENT_FORMATTER = new Intl.NumberFormat("tr-TR", {
  style: "percent",
  maximumFractionDigits: 0,
});

/** Plain number formatter (no currency, no percent). */
export const NUMBER_FORMATTER = new Intl.NumberFormat("tr-TR", {
  maximumFractionDigits: 2,
});
