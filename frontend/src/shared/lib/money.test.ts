import { describe, expect, it } from "vitest";

import { decimalFromMinor, decimalToMinor, formatMoney } from "./money";

describe("money boundaries", () => {
  it("renders minor units without losing a negative sign", () => {
    expect(decimalFromMinor(150050)).toBe("1500.50");
    expect(decimalFromMinor(-5)).toBe("-0.05");
  });

  it("accepts canonical positive minor-unit input and rejects unsafe values", () => {
    expect(decimalToMinor("1500.50")).toBe(150050);
    expect(decimalToMinor("0.00")).toBeUndefined();
    expect(decimalToMinor("1.005")).toBeUndefined();
    expect(decimalToMinor("999999999999999999")).toBeUndefined();
  });

  it("formats currency from integer minor units", () => {
    expect(formatMoney(150050, "TRY")).toContain("1.500,50");
  });
});
