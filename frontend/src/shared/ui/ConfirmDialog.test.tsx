import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "./ConfirmDialog";

function renderDialog(
  overrides: Partial<Parameters<typeof ConfirmDialog>[0]> = {},
) {
  const onCancel = vi.fn();
  const onConfirm = vi.fn();
  render(
    <ConfirmDialog
      titleId="t"
      title="Emin misiniz?"
      confirmLabel="Onayla"
      confirmPendingLabel="Kaydediliyor…"
      onCancel={onCancel}
      onConfirm={onConfirm}
      {...overrides}
    >
      <p>Gövde metni</p>
    </ConfirmDialog>,
  );
  return { onCancel, onConfirm };
}

describe("ConfirmDialog", () => {
  it("labels the dialog through aria-labelledby", () => {
    renderDialog();
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAttribute("aria-labelledby", "t");
    expect(
      screen.getByRole("heading", { name: "Emin misiniz?" }),
    ).toHaveAttribute("id", "t");
  });

  it("renders the body as children between the heading and the buttons", () => {
    renderDialog();
    expect(screen.getByText("Gövde metni")).toBeInTheDocument();
  });

  it("wires the two callbacks", async () => {
    const user = userEvent.setup();
    const { onCancel, onConfirm } = renderDialog();

    await user.click(screen.getByRole("button", { name: "Vazgeç" }));
    await user.click(screen.getByRole("button", { name: "Onayla" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("swaps to the pending label and disables both buttons while pending", () => {
    renderDialog({ pending: true });
    expect(
      screen.getByRole("button", { name: "Kaydediliyor…" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "Vazgeç" })).toBeDisabled();
  });

  it("renders the destructive button styling only when asked", () => {
    const { unmount } = render(
      <ConfirmDialog
        titleId="a"
        title="a"
        confirmLabel="Sil"
        confirmPendingLabel="…"
        confirmVariant="danger"
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      >
        <p>x</p>
      </ConfirmDialog>,
    );
    expect(screen.getByRole("button", { name: "Sil" })).toHaveClass(
      "danger-button",
    );
    unmount();

    renderDialog();
    expect(screen.getByRole("button", { name: "Onayla" })).toHaveClass(
      "primary-button",
    );
  });

  it("uses a distinct style class only for the warning variant", () => {
    const { unmount } = render(
      <ConfirmDialog
        titleId="a"
        title="a"
        confirmLabel="ok"
        confirmPendingLabel="…"
        variant="warning"
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
      >
        <p>x</p>
      </ConfirmDialog>,
    );
    const warningClassName = screen.getByRole("dialog").className;
    unmount();

    renderDialog();
    expect(screen.getByRole("dialog").className).not.toBe(warningClassName);
  });
});
