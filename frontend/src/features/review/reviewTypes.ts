import {
  decimalFromMinor,
  decimalToFiniteNumber,
  decimalToInteger,
} from "@/shared";
import type { components } from "../../generated/core-api";

export type ExtractedRule = components["schemas"]["ExtractedRule"];
export type RuleValue = components["schemas"]["RuleSetStructuredValue"];
export type Category = components["schemas"]["RuleCategory"];
export type Draft = {
  category: Category;
  title: string;
  description: string;
  value: ValueDraft;
  excluded: boolean;
};
export type ValueDraft = {
  type: RuleValue["type"];
  first: string;
  second: string;
  flag: boolean;
};

export const CATEGORIES: Category[] = [
  "PAYMENT",
  "DELIVERY",
  "QUALITY",
  "PENALTY",
  "TERMINATION",
  "DISPUTE",
  "OTHER",
  "UNKNOWN",
];
export const VALUE_TYPES: RuleValue["type"][] = [
  "TEXT",
  "MONEY",
  "PERCENTAGE",
  "DURATION",
  "DATE",
  "BOOLEAN",
  "QUANTITY",
];
export const CATEGORY_LABELS: Record<Category, string> = {
  PAYMENT: "Ödeme",
  DELIVERY: "Teslimat",
  QUALITY: "Kalite",
  PENALTY: "Ceza",
  TERMINATION: "Fesih",
  DISPUTE: "Uyuşmazlık",
  OTHER: "Diğer",
  UNKNOWN: "Belirsiz",
};

function draftValue(value: ExtractedRule["structuredValue"]): ValueDraft {
  switch (value.type) {
    case "TEXT":
      return { type: "TEXT", first: value.value, second: "", flag: false };
    case "MONEY":
      return {
        type: "MONEY",
        first: decimalFromMinor(value.amountMinor),
        second: value.currency,
        flag: false,
      };
    case "PERCENTAGE":
      return {
        type: "PERCENTAGE",
        first: decimalFromMinor(value.basisPoints),
        second: "",
        flag: false,
      };
    case "DURATION":
      return {
        type: "DURATION",
        first: String(value.valueSeconds),
        second: "",
        flag: false,
      };
    case "DATE":
      return { type: "DATE", first: value.value, second: "", flag: false };
    case "BOOLEAN":
      return { type: "BOOLEAN", first: "", second: "", flag: value.value };
    case "QUANTITY":
      return {
        type: "QUANTITY",
        first: String(value.value),
        second: value.unit,
        flag: false,
      };
  }
}

export function toRuleValue(value: ValueDraft): RuleValue | undefined {
  switch (value.type) {
    case "TEXT":
      return value.first.trim()
        ? { type: "TEXT", value: value.first.trim() }
        : undefined;
    case "MONEY": {
      const amountMinor = decimalToInteger(value.first, 2);
      return amountMinor === undefined ||
        !/^[A-Z]{3}$/.test(value.second.trim())
        ? undefined
        : { type: "MONEY", amountMinor, currency: value.second.trim() };
    }
    case "PERCENTAGE": {
      const basisPoints = decimalToInteger(value.first, 2);
      return basisPoints === undefined || basisPoints > 10_000
        ? undefined
        : { type: "PERCENTAGE", basisPoints };
    }
    case "DURATION": {
      const valueSeconds = decimalToInteger(value.first, 0);
      return valueSeconds === undefined
        ? undefined
        : { type: "DURATION", valueSeconds };
    }
    case "DATE":
      return /^\d{4}-\d{2}-\d{2}$/.test(value.first)
        ? { type: "DATE", value: value.first }
        : undefined;
    case "BOOLEAN":
      return { type: "BOOLEAN", value: value.flag };
    case "QUANTITY": {
      const amount = decimalToFiniteNumber(value.first);
      return amount === undefined || !value.second.trim()
        ? undefined
        : { type: "QUANTITY", value: amount, unit: value.second.trim() };
    }
  }
}

export function initialDraft(rule: ExtractedRule): Draft {
  return {
    category: rule.category,
    title: rule.title,
    description: rule.description,
    value: draftValue(rule.structuredValue),
    excluded: false,
  };
}

export function changed(rule: ExtractedRule, draft: Draft): boolean {
  return (
    draft.category !== rule.category ||
    draft.title !== rule.title ||
    draft.description !== rule.description ||
    JSON.stringify(toRuleValue(draft.value)) !==
      JSON.stringify(rule.structuredValue)
  );
}

export function formatValue(value: RuleValue): string {
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
  }
}

export function legalBasis(rule: {
  legalBasis?: components["schemas"]["AdvisoryLegalBasis"] | null;
  legalBasisProvenance?: string;
}) {
  return rule.legalBasis
    ? `${rule.legalBasis.source} · Md. ${rule.legalBasis.articleNo}${rule.legalBasisProvenance ? ` (${rule.legalBasisProvenance})` : ""}`
    : "Hukuki dayanak belirtilmedi";
}

export function getDecisionErrors(
  errors: Record<string, string>,
  decisionIndex: number,
) {
  const prefix = `decisions[${decisionIndex}].`;
  return Object.entries(errors).reduce<Record<string, string>>(
    (result, [field, message]) => {
      if (field.startsWith(prefix))
        result[field.slice(prefix.length)] = message;
      return result;
    },
    {},
  );
}

export function emptyAddedDraft(): Draft {
  return {
    category: "OTHER",
    title: "",
    description: "",
    value: { type: "TEXT", first: "", second: "", flag: false },
    excluded: false,
  };
}
