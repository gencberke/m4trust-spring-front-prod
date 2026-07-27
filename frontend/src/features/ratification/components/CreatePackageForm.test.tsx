import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { CreatePackageForm } from "./CreatePackageForm";

describe("CreatePackageForm", () => {
  it("validates client-side amount before submit", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(
      <CreatePackageForm
        hasCurrentPackage={false}
        ready
        suggestions={[]}
        suggestionsLoading={false}
        pending={false}
        error={undefined}
        onSubmit={onSubmit}
      />,
    );

    await user.type(screen.getByLabelText("Sözleşme bedeli (ondalık)"), "abc");
    await user.type(screen.getByLabelText("Para birimi (ISO 4217)"), "TRY");
    await user.type(screen.getByLabelText("İtiraz penceresi (gün)"), "7");
    await user.click(
      screen.getByRole("button", { name: "Tutarı teyit et ve onaya sun" }),
    );

    expect(onSubmit).not.toHaveBeenCalled();
    expect(
      screen.getByText("Geçerli bir pozitif tutar girin."),
    ).toBeInTheDocument();
  });

  it("submits normalized commercial terms", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(
      <CreatePackageForm
        hasCurrentPackage={false}
        ready
        suggestions={[]}
        suggestionsLoading={false}
        pending={false}
        error={undefined}
        onSubmit={onSubmit}
      />,
    );

    await user.type(
      screen.getByLabelText("Sözleşme bedeli (ondalık)"),
      "1500.50",
    );
    await user.type(screen.getByLabelText("Para birimi (ISO 4217)"), "try");
    await user.type(screen.getByLabelText("İtiraz penceresi (gün)"), "14");
    await user.click(
      screen.getByRole("button", { name: "Tutarı teyit et ve onaya sun" }),
    );

    expect(onSubmit).toHaveBeenCalledWith(
      { amountMinor: 150050, currency: "TRY" },
      14,
      "REQUIRED",
    );
  });
});
