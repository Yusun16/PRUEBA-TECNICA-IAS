import { test, expect } from "@playwright/test";
const result = {
  reference: "PAY-AX91",
  outcome: "RESOLVED",
  reason: "APPROVED_RECONCILED",
  paymentStatus: "CONFIRMED",
  netDebitBefore: 125000,
  netDebitAfter: 125000,
  movementsCreated: 0,
  auditCreated: true,
};
async function login(page) {
  await page.route("**/api/support/session", (r) =>
    r.fulfill({ json: { username: "n3", role: "SUPPORT" } }),
  );
  await page.goto("/login");
  await page.locator("#username").fill("n3");
  await page.locator("#password").fill("demo-change-me");
  await page.locator("#login-button").click();
  await expect(page.locator("#dashboard")).toBeVisible();
}
test("empty activity survives login and logout, then transitions in newest-first order", async ({
  page,
}) => {
  let calls = 0;
  await page.route("**/api/support/payments/**", (r) =>
    r.fulfill({
      json: {
        ...result,
        ...(++calls === 2
          ? {
              outcome: "MANUAL_REVIEW",
              reason: "DUPLICATE_DEBITS_REQUIRE_REVIEW",
              auditCreated: false,
            }
          : {}),
      },
    }),
  );
  await login(page);
  await expect(page.locator("#recent-activity table")).toHaveCount(0);
  await expect(page.locator("#recent-activity")).toContainText(
    "Las operaciones de esta ventana aparecerán aquí",
  );
  await page.locator("#show-activity").click();
  await expect(page.locator("#all-activity")).toContainText(
    "Todavía no hay operaciones",
  );
  await expect(page.locator("#all-activity table")).toHaveCount(0);
  await page.locator("#back-to-reconcile").click();
  await page.locator("#reference").fill("PAY-AX91");
  for (const total of ["1", "2"]) {
    await page.locator("#submit-button").click();
    await expect(page.locator("#metric-total")).toHaveText(total);
  }
  await expect(page.locator("#recent-activity tbody tr")).toHaveCount(2);
  await expect(page.locator("#recent-activity tbody tr").first()).toContainText(
    "Revisión manual",
  );
  await expect(page.locator("#recent-activity tbody tr").last()).toContainText(
    "Corregido",
  );
  await page.locator("#logout-button").click();
  await login(page);
  await expect(page.locator("#recent-activity table")).toHaveCount(0);
  await expect(page.locator("#all-activity table")).toHaveCount(0);
});
for (const width of [375, 390, 768, 1024, 1440])
  test(`keyboard and readable layout at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.route("**/api/support/session", (r) =>
      r.fulfill({ json: { username: "n3", role: "SUPPORT" } }),
    );
    await page.route("**/api/support/payments/**", (r) =>
      r.fulfill({ json: result }),
    );
    await page.goto("/login");
    await expect(page.locator("#username")).toBeFocused();
    await page.keyboard.type("n3");
    await page.keyboard.press("Tab");
    await expect(page.locator("#password")).toBeFocused();
    await page.keyboard.type("demo-change-me");
    await page.keyboard.press("Tab");
    await expect(page.locator("#password-toggle")).toBeFocused();
    const touch = await page.locator("#password-toggle").boundingBox();
    expect(touch.width).toBeGreaterThanOrEqual(24);
    expect(touch.height).toBeGreaterThanOrEqual(24);
    await page.keyboard.press("Tab");
    await expect(page.locator("#login-button")).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#dashboard")).toBeVisible();
    await expect(page.locator("#main")).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(page.locator("#reference")).toBeFocused();
    await page.keyboard.type("PAY-AX91");
    await page.keyboard.press("Tab");
    await expect(page.locator("#submit-button")).toBeFocused();
    expect(
      await page
        .locator("#submit-button")
        .evaluate((el) => getComputedStyle(el).outlineStyle),
    ).not.toBe("none");
    await page.keyboard.press("Enter");
    await expect(page.locator(".result-status.success")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    const measurements = await page.evaluate(() => {
      const rgb = (x) => x.match(/[\d.]+/g).map(Number);
      const lum = (c) =>
        c
          .slice(0, 3)
          .map((v) => {
            v /= 255;
            return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
          })
          .reduce((n, x, i) => n + x * [0.2126, 0.7152, 0.0722][i], 0);
      return [
        ".field-help",
        ".action-notice p",
        ".metric-label",
        ".mini-label",
        ".result-status p",
        ".badge",
        ".result-reason",
        ".breadcrumb",
        ".page-heading p",
        ".card-intro",
        ".text-button",
      ].map((selector) => {
        const el = document.querySelector(selector),
          style = getComputedStyle(el);
        let parent = el,
          bg;
        while (parent) {
          bg = rgb(getComputedStyle(parent).backgroundColor);
          if (bg.length === 3 || bg[3] === 1) break;
          parent = parent.parentElement;
        }
        const a = lum(rgb(style.color)),
          b = lum(bg);
        return {
          selector,
          size: parseFloat(style.fontSize),
          ratio: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05),
        };
      });
    });
    for (const m of measurements) {
      expect(m.size, m.selector).toBeGreaterThanOrEqual(12);
      expect(m.ratio, m.selector).toBeGreaterThanOrEqual(4.5);
    }
    await page
      .getByRole("button", { name: "Actividad de sesión", exact: true })
      .click();
    const region = page
      .getByRole("region", { name: "Operaciones de sesión; tabla desplazable" })
      .filter({ visible: true });
    await page.keyboard.press("Tab");
    await expect(page.locator("#back-to-reconcile")).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(region).toBeFocused();
    expect(
      await region.evaluate((el) => getComputedStyle(el).outlineStyle),
    ).not.toBe("none");
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });
for (const status of [400, 403, 409, 500, 502, 504])
  test(`HTTP ${status} remains actionable and sanitized`, async ({ page }) => {
    await page.route("**/api/support/payments/**", (r) =>
      r.fulfill({ status, json: { detail: "secret SQL Authorization" } }),
    );
    await login(page);
    await page.locator("#reference").fill("PAY-AX91");
    await page.locator("#submit-button").click();
    if (status === 403) {
      await expect(page.locator("#login-view")).toBeVisible();
      await expect(page.locator("#username")).toBeFocused();
    } else {
      await expect(page.locator("#result-label")).toHaveText(
        "SIN RESULTADO CONFIRMADO",
      );
      await expect(page.locator("#result-content")).toContainText(
        status === 409
          ? "Otra operación"
          : status === 504
            ? "podría haberse procesado"
            : "No",
      );
    }
    await expect(page.locator("body")).not.toContainText(
      "secret SQL Authorization",
    );
  });
test("malicious reason is not inserted as HTML and large monetary tokens remain readable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 1000 });
  await page.route("**/api/support/payments/**", (r) =>
    r.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...result,
        reason: '<img src=x onerror="window.compromised=true">',
      }).replace(/125000/g, "9999999999999999.99"),
    }),
  );
  await login(page);
  await page.locator("#reference").fill("PAY-AX91");
  await page.locator("#submit-button").click();
  await expect(page.locator(".result-status.success")).toBeVisible();
  await expect(page.locator(".amount-item strong").last()).toHaveText(
    "9.999.999.999.999.999,99",
  );
  await expect(page.locator("#result-content img")).toHaveCount(0);
  expect(await page.evaluate(() => window.compromised)).toBeUndefined();
  for (const element of await page.locator(".amount-item strong").all())
    expect(
      await element.evaluate((el) => el.scrollWidth <= el.clientWidth),
    ).toBe(true);
});
test("interrupted connection is uncertain and sends no automatic retry", async ({
  page,
}) => {
  let calls = 0;
  await page.route("**/api/support/payments/**", (r) => {
    calls++;
    return r.abort("connectionreset");
  });
  await login(page);
  await page.locator("#reference").fill("PAY-AX91");
  await page.locator("#submit-button").click();
  await expect(page.locator("#result-content")).toContainText(
    "podría haber terminado",
  );
  await expect(page.locator("#result-label")).toHaveText(
    "SIN RESULTADO CONFIRMADO",
  );
  expect(calls).toBe(1);
});
