import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { DealActionsPanel } from "./DealActionsPanel";

describe("DealActionsPanel", () => {
  it("shows cancel control only when the server allows cancellation", () => {
    const { rerender } = render(
      <DealActionsPanel
        canCancel
        confirmationOpen={false}
        isPending={false}
        onOpen={vi.fn()}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Anlaşmayı iptal et" }),
    ).toBeInTheDocument();

    rerender(
      <DealActionsPanel
        canCancel={false}
        confirmationOpen={false}
        isPending={false}
        onOpen={vi.fn()}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Anlaşmayı iptal et" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText("Bu anlaşma için kullanılabilir iptal işlemi yok."),
    ).toBeInTheDocument();
  });

  it("opens the confirmation dialog and wires confirm/cancel", async () => {
    const user = userEvent.setup();
    const onOpen = vi.fn();
    const onClose = vi.fn();
    const onConfirm = vi.fn();

    const { rerender } = render(
      <DealActionsPanel
        canCancel
        confirmationOpen={false}
        isPending={false}
        onOpen={onOpen}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Anlaşmayı iptal et" }),
    );
    expect(onOpen).toHaveBeenCalledTimes(1);

    rerender(
      <DealActionsPanel
        canCancel
        confirmationOpen
        isPending={false}
        onOpen={onOpen}
        onClose={onClose}
        onConfirm={onConfirm}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Vazgeç" }));
    await user.click(screen.getByRole("button", { name: "İptali onayla" }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("disables confirmation actions while pending", () => {
    render(
      <DealActionsPanel
        canCancel
        confirmationOpen
        isPending
        onOpen={vi.fn()}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("button", { name: "İptal ediliyor…" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "Vazgeç" })).toBeDisabled();
  });
});
