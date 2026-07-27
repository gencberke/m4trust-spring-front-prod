import { describe, expect, it } from "vitest";

import {
  decimalFromMinor,
  decimalToFiniteNumber,
  decimalToInteger,
  decimalToMinor,
  formatMoney,
} from "./money";

describe("decimalFromMinor", () => {
  it("renders whole and fractional minor units", () => {
    expect(decimalFromMinor(150050)).toBe("1500.50");
    expect(decimalFromMinor(100)).toBe("1.00");
    expect(decimalFromMinor(5)).toBe("0.05");
    expect(decimalFromMinor(0)).toBe("0.00");
  });

  it("is sign-safe for small negative amounts", () => {
    // The former app/money.ts padded String(-5) to "0-5" and returned "0.-5".
    expect(decimalFromMinor(-5)).toBe("-0.05");
    expect(decimalFromMinor(-99)).toBe("-0.99");
    expect(decimalFromMinor(-150050)).toBe("-1500.50");
  });
});

describe("decimalToInteger", () => {
  it("scales by the requested number of fraction digits", () => {
    expect(decimalToInteger("1500.50", 2)).toBe(150050);
    expect(decimalToInteger("1500.5", 2)).toBe(150050);
    expect(decimalToInteger("1500", 2)).toBe(150000);
    expect(decimalToInteger("30", 0)).toBe(30);
  });

  it("accepts a comma as the decimal separator", () => {
    expect(decimalToInteger("1500,50", 2)).toBe(150050);
  });

  it("rejects more fraction digits than the scale allows", () => {
    expect(decimalToInteger("1.005", 2)).toBeUndefined();
    expect(decimalToInteger("1.5", 0)).toBeUndefined();
  });

  it("rejects malformed text", () => {
    expect(decimalToInteger("", 2)).toBeUndefined();
    expect(decimalToInteger("abc", 2)).toBeUndefined();
    expect(decimalToInteger("1.2.3", 2)).toBeUndefined();
  });

  it("enforces the minimum bound", () => {
    expect(decimalToInteger("0", 2, 0)).toBe(0);
    expect(decimalToInteger("0", 2, 1)).toBeUndefined();
    expect(decimalToInteger("-1", 0, 0)).toBeUndefined();
    expect(decimalToInteger("-1", 0, -100)).toBe(-1);
  });

  it("rejects values beyond the safe integer range", () => {
    expect(decimalToInteger("999999999999999999", 2)).toBeUndefined();
  });
});

describe("decimalToMinor", () => {
  it("keeps the positive-only, scale-2 contract of the old helper", () => {
    expect(decimalToMinor("1500.50")).toBe(150050);
    expect(decimalToMinor("0.01")).toBe(1);
    expect(decimalToMinor("0")).toBeUndefined();
    expect(decimalToMinor("0.00")).toBeUndefined();
    expect(decimalToMinor("-1.00")).toBeUndefined();
    expect(decimalToMinor("1.005")).toBeUndefined();
  });
});

describe("decimalToFiniteNumber", () => {
  it("parses decimal text into a plain number", () => {
    expect(decimalToFiniteNumber("12.5")).toBe(12.5);
    expect(decimalToFiniteNumber("-3")).toBe(-3);
    expect(decimalToFiniteNumber("12,5")).toBe(12.5);
  });

  it("rejects malformed text", () => {
    expect(decimalToFiniteNumber("abc")).toBeUndefined();
    expect(decimalToFiniteNumber("")).toBeUndefined();
  });
});

describe("formatMoney", () => {
  it("renders a localized currency string from minor units", () => {
    const formatted = formatMoney(150050, "TRY");
    expect(formatted).toContain("1.500,50");
  });

  it("caches one formatter per currency without mixing output", () => {
    expect(formatMoney(150050, "USD")).not.toBe(formatMoney(150050, "TRY"));
    expect(formatMoney(100, "TRY")).toContain("1,00");
  });
});
