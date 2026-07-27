import { describe, expect, it } from "vitest";

import {
  formatDate,
  formatDateOnly,
  formatDateOrUndefined,
} from "./formatDate";

const TIMESTAMP = "2026-07-27T09:30:00Z";

describe("formatDate", () => {
  it("defaults to the long date + short time variant", () => {
    expect(formatDate(TIMESTAMP)).toBe(formatDate(TIMESTAMP, "long-short"));
  });

  it("renders each variant distinctly", () => {
    const long = formatDate(TIMESTAMP, "long-short");
    const medium = formatDate(TIMESTAMP, "medium-short");
    const dateOnly = formatDate(TIMESTAMP, "long-date-only");

    expect(long).not.toBe(medium);
    expect(long).toContain("2026");
    expect(medium).toContain("2026");
    // Only the date-only variant omits the time component.
    expect(dateOnly).not.toMatch(/\d{2}:\d{2}/);
    expect(long).toMatch(/\d{2}:\d{2}/);
    expect(medium).toMatch(/\d{2}:\d{2}/);
  });

  it("throws on an unparsable value, as the previous helpers did", () => {
    expect(() => formatDate("not-a-date")).toThrow(RangeError);
  });
});

describe("formatDateOrUndefined", () => {
  it("returns undefined for null and undefined", () => {
    expect(formatDateOrUndefined(null)).toBeUndefined();
    expect(formatDateOrUndefined(undefined)).toBeUndefined();
    expect(formatDateOrUndefined("")).toBeUndefined();
  });

  it("defaults to the medium variant used by the analysis panel", () => {
    expect(formatDateOrUndefined(TIMESTAMP)).toBe(
      formatDate(TIMESTAMP, "medium-short"),
    );
  });
});

describe("formatDateOnly", () => {
  it("anchors a bare date to UTC midnight so the day never shifts", () => {
    // With local-time parsing, a negative UTC offset would render 2026-07-26.
    expect(formatDateOnly("2026-07-27")).toContain("27");
    expect(formatDateOnly("2026-01-01")).toContain("1");
    expect(formatDateOnly("2026-01-01")).toContain("2026");
  });

  it("omits the time component", () => {
    expect(formatDateOnly("2026-07-27")).not.toMatch(/\d{2}:\d{2}/);
  });
});
