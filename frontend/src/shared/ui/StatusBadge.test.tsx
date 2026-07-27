import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("keeps the per-domain CSS class so feature colour rules still match", () => {
    const { container, rerender } = render(
      <StatusBadge
        domain="funding"
        status="PENDING"
        label="Ödeme bekleniyor"
      />,
    );
    expect(container.firstElementChild).toHaveClass("funding-status-badge");

    rerender(
      <StatusBadge
        domain="ratification"
        status="PENDING"
        label="Onay bekliyor"
      />,
    );
    expect(container.firstElementChild).toHaveClass(
      "ratification-status-badge",
    );
  });

  it("exposes the raw enum through data-status", () => {
    render(<StatusBadge domain="deal" status="ACTIVE" label="Aktif" />);
    expect(screen.getByText("Aktif")).toHaveAttribute("data-status", "ACTIVE");
  });

  it("renders the caller-supplied label, never the raw status", () => {
    render(<StatusBadge domain="deal" status="ACTIVE" label="Teslimat" />);
    expect(screen.getByText("Teslimat")).toBeInTheDocument();
    expect(screen.queryByText("ACTIVE")).not.toBeInTheDocument();
  });

  it("omits data-cancelled unless the flag is set", () => {
    const { rerender, container } = render(
      <StatusBadge
        domain="evidence"
        status="PENDING_UPLOAD"
        label="Yüklenecek"
      />,
    );
    expect(container.firstElementChild).not.toHaveAttribute("data-cancelled");

    rerender(
      <StatusBadge
        domain="evidence"
        status="PENDING_UPLOAD"
        label="Yükleme iptal edildi"
        cancelled
      />,
    );
    expect(container.firstElementChild).toHaveAttribute(
      "data-cancelled",
      "true",
    );
  });
});
