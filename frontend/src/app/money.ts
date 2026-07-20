/**
 * Shared minor-unit <-> decimal money helpers. Amounts always travel the wire
 * as integer minor units; conversion uses BigInt so no binary float ever
 * touches a monetary value (ADR-006 §28).
 */

/** Converts amountMinor (integer minor units) to a decimal display string; no binary float involved. */
export function decimalFromMinor(amountMinor: number): string {
  const digits = String(amountMinor).padStart(3, "0");
  return `${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/** Converts decimal text to a positive integer minor-unit amount using BigInt; float never touches the wire. */
export function decimalToMinor(text: string): number | undefined {
  const normalized = text.trim().replace(",", ".");
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) return undefined;
  const [wholeRaw, fractionRaw = ""] = normalized.split(".");
  if (fractionRaw.length > 2) return undefined;
  const integer = BigInt(`${wholeRaw}${fractionRaw.padEnd(2, "0")}`);
  if (integer < 1n || integer > BigInt(Number.MAX_SAFE_INTEGER)) return undefined;
  return Number(integer);
}
