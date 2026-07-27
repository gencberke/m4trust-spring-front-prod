import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { Draft } from "../reviewTypes";
import { RuleEditor } from "./RuleEditor";

const draft: Draft = {
  category: "DELIVERY",
  title: "Teslim süresi",
  description: "Açıklama",
  excluded: false,
  value: { type: "TEXT", first: "30 gün", second: "", flag: false },
};

describe("RuleEditor", () => {
  it("forwards field edits through onChange", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    render(
      <RuleEditor
        draft={draft}
        errors={{}}
        idPrefix="rule-1"
        onChange={onChange}
      />,
    );

    const titleInput = screen.getByLabelText("Başlık");
    await user.clear(titleInput);
    await user.type(titleInput, "Yeni başlık");
    expect(onChange).toHaveBeenCalled();

    await user.selectOptions(screen.getByLabelText("Kategori"), "PAYMENT");
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ category: "PAYMENT" }),
    );
  });

  it("shows validation messages and disables inputs when excluded", () => {
    render(
      <RuleEditor
        draft={{ ...draft, excluded: true }}
        errors={{ title: "Başlık zorunludur." }}
        idPrefix="rule-2"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Başlık zorunludur.")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Teslim süresi")).toBeDisabled();
  });
});
