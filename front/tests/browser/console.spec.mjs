import { test, expect } from "@playwright/test";

const corrected = {
  reference: "PAY-BX17",
  outcome: "RESOLVED",
  reason: "APPROVED_RECONCILED",
  paymentStatus: "CONFIRMED",
  netDebitBefore: 178000,
  netDebitAfter: 89000,
  movementsCreated: 1,
  auditCreated: true,
};

async function connect(page) {
  await page.getByRole("button", { name: "Configurar acceso" }).click();
  await page.getByLabel("Usuario", { exact: true }).fill("n3");
  await page.getByLabel("Contraseña", { exact: true }).fill("demo-change-me");
  await page.getByRole("button", { name: "Guardar acceso" }).click();
  await page.locator("#reference").fill("PAY-BX17");
}

test("initial state is honest and configuring credentials does not reconcile", async ({
  page,
}) => {
  let commands = 0;
  await page.route("**/api/**", (route) => {
    commands++;
    return route.fulfill({ json: corrected });
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "La claridad empieza aquí" }),
  ).toBeVisible();
  await expect(page.locator("#reference")).toHaveValue("");
  await expect(page.locator("body")).not.toContainText("PAY-BX17");
  await page.locator("#reference").fill("PAY-BX17");
  await page
    .getByRole("button", { name: "Conciliar pago", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByLabel("Usuario", { exact: true }).fill("n3");
  await page.getByLabel("Contraseña", { exact: true }).fill("demo-change-me");
  await page.getByRole("button", { name: "Guardar acceso" }).click();
  await page.locator("#reference").fill("PAY-BX17");
  expect(commands).toBe(0);
  expect(
    await page.evaluate(() => [localStorage.length, sessionStorage.length]),
  ).toEqual([0, 0]);
});

test("shows correction, then an idempotent result and real session history", async ({
  page,
}) => {
  let calls = 0;
  await page.route("**/api/**", async (route) => {
    calls++;
    expect(route.request().headers().authorization).toBe(
      "Basic bjM6ZGVtby1jaGFuZ2UtbWU=",
    );
    await route.fulfill({
      json:
        calls === 1
          ? corrected
          : {
              ...corrected,
              outcome: "ALREADY_CONSISTENT",
              reason: "NO_CHANGES_REQUIRED",
              netDebitBefore: 89000,
              movementsCreated: 0,
              auditCreated: false,
            },
    });
  });
  await page.goto("/");
  await connect(page);
  await page
    .getByRole("button", { name: "Conciliar pago", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Pago conciliado" }),
  ).toBeVisible();
  await expect(page.locator("#result-content")).toContainText("89.000,00");
  await page
    .getByRole("button", { name: "Conciliar pago", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "El pago ya está consistente" }),
  ).toBeVisible();
  await expect(page.locator("#result-content")).toContainText(
    "Sin registro adicional",
  );
  await expect(page.locator("#metric-total")).toHaveText("2");
  await expect(page.locator("#metric-resolved")).toHaveText("1");
  await page.getByRole("button", { name: "Ver actividad" }).click();
  await expect(page.locator("#all-activity tbody tr")).toHaveCount(2);
  await page
    .locator("#all-activity")
    .getByRole("button", { name: "PAY-BX17" })
    .first()
    .click();
  await expect(page.locator("#reference")).toHaveValue("PAY-BX17");
  expect(calls).toBe(2);
});

test("contradictory evidence is clearly manual review with zero movements", async ({
  page,
}) => {
  await page.route("**/api/**", (route) =>
    route.fulfill({
      json: {
        ...corrected,
        reference: "PAY-CQ22",
        outcome: "MANUAL_REVIEW",
        reason: "CONTRADICTORY_PROVIDER_EVIDENCE",
        paymentStatus: "PENDING",
        netDebitBefore: 0,
        netDebitAfter: 0,
        movementsCreated: 0,
      },
    }),
  );
  await page.goto("/");
  await connect(page);
  await page.locator("#reference").fill("PAY-CQ22");
  await page
    .getByRole("button", { name: "Conciliar pago", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Requiere revisión manual" }),
  ).toBeVisible();
  await expect(page.locator("#result-content")).toContainText("Pendiente");
  await expect(page.locator("#metric-manual")).toHaveText("1");
});

test("validation blocks invalid references before sending requests", async ({
  page,
}) => {
  let calls = 0;
  await page.route("**/api/**", (route) => {
    calls++;
    return route.fulfill({ json: corrected });
  });
  await page.goto("/");
  await connect(page);
  await page.getByLabel("Referencia del pago").fill("bad.reference");
  await page
    .getByRole("button", { name: "Conciliar pago", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("referencia válida");
  expect(calls).toBe(0);
});

test("loading blocks duplicate submissions and stale results are labelled", async ({
  page,
}) => {
  let calls = 0;
  await page.route("**/api/**", async (route) => {
    calls++;
    await new Promise((resolve) => setTimeout(resolve, 600));
    await route.fulfill({ json: corrected });
  });
  await page.goto("/");
  await connect(page);
  await page
    .getByRole("button", { name: "Conciliar pago", exact: true })
    .click();
  await expect(page.locator("#submit-button")).toBeDisabled();
  await expect(page.locator("#reference")).toBeDisabled();
  await expect(
    page.getByRole("heading", { name: "Pago conciliado" }),
  ).toBeVisible();
  expect(calls).toBe(1);
  await page.locator("#reference").fill("PAY-AX91");
  await expect(page.locator("#stale-note")).toContainText("PAY-BX17");
});

for (const [status, text] of [
  [401, "credenciales"],
  [404, "No encontramos"],
  [503, "Verifica el estado"],
]) {
  test(`HTTP ${status} shows an actionable error without backend internals`, async ({
    page,
  }) => {
    await page.route("**/api/**", (route) =>
      route.fulfill({ status, json: { detail: "secret SQL password" } }),
    );
    await page.goto("/");
    await connect(page);
    await page
      .getByRole("button", { name: "Conciliar pago", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "No pudimos confirmar el resultado" }),
    ).toBeVisible();
    await expect(page.locator("#result-content")).toContainText(text);
    await expect(page.locator("body")).not.toContainText("secret SQL password");
    if (status === 401)
      await expect(
        page.getByRole("button", { name: "Configurar acceso" }),
      ).toBeVisible();
  });
}

test("mobile layout is usable without horizontal page overflow", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByLabel("Referencia del pago")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await connect(page);
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.screenshot({ path: "test-results/mobile.png", fullPage: true });
});

test("desktop renders without console or resource errors", async ({ page }) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Conciliación de pagos." }),
  ).toBeVisible();
  await page.screenshot({ path: "test-results/desktop.png", fullPage: true });
  expect(errors).toEqual([]);
});
