import { decimalFromMinor } from "@/shared";
import type { components } from "../../generated/core-api";
import type { RatificationPackageDetail } from "./ratificationApi";

export type StructuredValue = components["schemas"]["RuleSetStructuredValue"];
export type RatificationPackageSnapshotV2 =
  components["schemas"]["RatificationPackageSnapshotV2"];
export type RatificationPackageSnapshotV3 =
  components["schemas"]["RatificationPackageSnapshotV3"];
export type EvidencePolicy = components["schemas"]["EvidencePolicy"];

export function isSnapshotV2(
  snapshot: RatificationPackageDetail["snapshot"],
): snapshot is RatificationPackageSnapshotV2 {
  return snapshot.schemaVersion === "2";
}

export function isSnapshotV3(
  snapshot: RatificationPackageDetail["snapshot"],
): snapshot is RatificationPackageSnapshotV3 {
  return snapshot.schemaVersion === "3";
}

export function hasDisputeWindow(
  snapshot: RatificationPackageDetail["snapshot"],
): snapshot is RatificationPackageSnapshotV2 | RatificationPackageSnapshotV3 {
  return isSnapshotV2(snapshot) || isSnapshotV3(snapshot);
}

export function effectiveEvidencePolicy(
  snapshot: RatificationPackageDetail["snapshot"],
): EvidencePolicy {
  return isSnapshotV3(snapshot) ? snapshot.evidencePolicy : "REQUIRED";
}

export const EVIDENCE_POLICY_LABELS: Record<EvidencePolicy, string> = {
  REQUIRED: "Kanıt gerekli",
  NOT_REQUIRED: "Kanıt gerekli değil",
};

const PACKAGE_STATUS_LABELS: Record<string, string> = {
  PENDING: "Onay bekliyor",
  RATIFIED: "Onaylandı",
  REJECTED: "Reddedildi",
  SUPERSEDED: "Yerine yeni koşullar sunuldu",
};

export function packageStatusLabel(status: string): string {
  return PACKAGE_STATUS_LABELS[status] ?? status;
}

export function formatStructuredValue(value: StructuredValue): string {
  switch (value.type) {
    case "TEXT":
      return value.value;
    case "MONEY":
      return `${decimalFromMinor(value.amountMinor)} ${value.currency}`;
    case "PERCENTAGE":
      return `%${decimalFromMinor(value.basisPoints)}`;
    case "DURATION":
      return `${value.valueSeconds} saniye`;
    case "DATE":
      return value.value;
    case "BOOLEAN":
      return value.value ? "Evet" : "Hayır";
    case "QUANTITY":
      return `${value.value} ${value.unit}`;
    default:
      return "Bilinmeyen değer";
  }
}

export function truncateHex(value: string, head = 10, tail = 8): string {
  return value.length > head + tail + 1
    ? `${value.slice(0, head)}…${value.slice(-tail)}`
    : value;
}

export interface MoneySuggestion {
  ruleReference: string;
  title: string;
  amountMinor: number;
  currency: string;
}

interface MoneySourceRule {
  ruleReference: string;
  title: string;
  structuredValue: StructuredValue;
}

export function extractMoneySuggestions(
  rules: MoneySourceRule[] | undefined,
): MoneySuggestion[] {
  if (!rules) return [];
  return rules
    .filter((rule) => rule.structuredValue.type === "MONEY")
    .map((rule) => {
      const value = rule.structuredValue as Extract<
        StructuredValue,
        { type: "MONEY" }
      >;
      return {
        ruleReference: rule.ruleReference,
        title: rule.title,
        amountMinor: value.amountMinor,
        currency: value.currency,
      };
    });
}
