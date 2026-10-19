import { chromium } from "@playwright/test";
import { createAppServer } from "../server.mjs";
import { mkdir, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
// Controlled UI fixtures only; this script never forwards financial commands to Java.
const stage = process.argv[2] ?? "final";
const directory =
  stage === "baseline"
    ? ".tools/visual-baseline"
    : "../docs/evidencia/frontend-final";
await mkdir(directory, { recursive: true });
const server = createAppServer();
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const executablePath =
  process.env.CHROME_PATH ??
  [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  ].find(existsSync);
let browser;
const observations = [];
try {
  browser = await chromium.launch(executablePath ? { executablePath } : {});
  for (const width of [375, 390, 768, 1024, 1440]) {
    const page = await browser.newPage({ viewport: { width, height: 1000 } });
    await page.route("**/api/support/session", (r) =>
      r.fulfill({ json: { username: "n3", role: "SUPPORT" } }),
    );
    await page.route("**/api/support/payments/**", (r) =>
      r.fulfill({
        json: {
          reference: "PAY-AX91",
          outcome: "RESOLVED",
          reason: "APPROVED_RECONCILED",
          paymentStatus: "CONFIRMED",
          netDebitBefore: 125000,
          netDebitAfter: 125000,
          movementsCreated: 0,
          auditCreated: true,
        },
      }),
    );
    await page.goto(`http://127.0.0.1:${server.address().port}/login`);
    for (const view of ["login", "dashboard", "result"]) {
      if (view === "dashboard") {
        await page.locator("#username").fill("n3");
        await page.locator("#password").fill("demo-change-me");
        await page.locator("#login-button").click();
        await page.locator("#dashboard").waitFor({ state: "visible" });
      }
      if (view === "result") {
        await page.locator("#reference").fill("PAY-AX91");
        await page.locator("#submit-button").click();
        await page.locator(".result-status.success").waitFor();
      }
      const metrics = await page.evaluate(() => {
        const luminance = (rgb) =>
          rgb
            .slice(0, 3)
            .map((x) => {
              x /= 255;
              return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
            })
            .reduce((n, x, i) => n + x * [0.2126, 0.7152, 0.0722][i], 0);
        const rgb = (x) => (x.match(/[\d.]+/g) || []).map(Number);
        const selectors = [
          ".login-intro",
          ".login-help p",
          ".login-footer",
          ".field-help",
          ".action-notice p",
          ".metric-label",
          ".mini-label",
          ".empty-result p",
          ".activity-empty small",
          ".result-status p",
          ".badge",
          ".result-reason",
        ];
        const lowContrastText = [];
        for (const el of document.querySelectorAll("body *")) {
          if (
            !el.checkVisibility() ||
            el.closest('[aria-hidden="true"],.brand') ||
            !Array.from(el.childNodes).some(
              (n) => n.nodeType === 3 && n.textContent.trim(),
            )
          )
            continue;
          const s = getComputedStyle(el);
          let parent = el,
            bg;
          while (parent) {
            bg = rgb(getComputedStyle(parent).backgroundColor);
            if (bg.length === 3 || bg[3] === 1) break;
            parent = parent.parentElement;
          }
          bg = parent ? bg : [255, 255, 255];
          const a = luminance(rgb(s.color)),
            b = luminance(bg),
            contrast = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05),
            size = parseFloat(s.fontSize),
            large =
              size >= 24 || (size >= 18.67 && parseInt(s.fontWeight) >= 700);
          if (contrast < (large ? 3 : 4.5))
            lowContrastText.push({
              selector:
                el.tagName.toLowerCase() +
                (el.id
                  ? "#" + el.id
                  : "." + String(el.className).replaceAll(" ", ".")),
              color: s.color,
              contrast,
            });
        }
        return {
          overflow: document.documentElement.scrollWidth > innerWidth,
          emptyActivityTables: document.querySelectorAll(
            "#recent-activity table",
          ).length,
          lowContrastText,
          text: selectors.flatMap((selector) => {
            const el = document.querySelector(selector);
            if (!el?.checkVisibility()) return [];
            const s = getComputedStyle(el);
            let parent = el,
              bg;
            while (parent) {
              bg = rgb(getComputedStyle(parent).backgroundColor);
              if (bg.length === 3 || bg[3] === 1) break;
              parent = parent.parentElement;
            }
            bg = parent ? bg : [255, 255, 255];
            const a = luminance(rgb(s.color)),
              b = luminance(bg);
            return [
              {
                selector,
                fontSize: parseFloat(s.fontSize),
                contrast: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05),
              },
            ];
          }),
        };
      });
      observations.push({ width, view, ...metrics });
      if (view !== "result" || width === 390 || width === 1440)
        await page.screenshot({
          path: `${directory}/${view}-${width}.png`,
          fullPage: true,
        });
    }
    await page.close();
  }
  if (stage === "final")
    for (const width of [390, 1440]) {
      const page = await browser.newPage({ viewport: { width, height: 1000 } });
      let count = 0;
      await page.route("**/api/support/session", (r) =>
        r.fulfill({ json: { username: "n3", role: "SUPPORT" } }),
      );
      await page.route("**/api/support/payments/**", (r) => {
        count++;
        return count === 3
          ? r.fulfill({ status: 409, json: { title: "PAYMENT_BUSY" } })
          : r.fulfill({
              json: {
                reference: count === 1 ? "PAY-DK44" : "PAY-BX17",
                outcome: count === 1 ? "ALREADY_CONSISTENT" : "MANUAL_REVIEW",
                reason:
                  count === 1
                    ? "NO_CHANGES_REQUIRED"
                    : "DUPLICATE_DEBITS_REQUIRE_REVIEW",
                paymentStatus: "CONFIRMED",
                netDebitBefore: count === 1 ? 31000 : 178000,
                netDebitAfter: count === 1 ? 31000 : 178000,
                movementsCreated: 0,
                auditCreated: count === 2,
              },
            });
      });
      await page.goto(`http://127.0.0.1:${server.address().port}/login`);
      await page.locator("#username").fill("n3");
      await page.locator("#password").fill("demo-change-me");
      await page.locator("#login-button").click();
      await page.locator("#dashboard").waitFor({ state: "visible" });
      await page.locator("#reference").fill("PAY-DK44");
      for (const [name, selector, reference] of [
        ["consistent", ".result-status.neutral", "PAY-DK44"],
        ["manual", ".result-status.warning", "PAY-BX17"],
        ["error", ".result-error", "PAY-AX91"],
      ]) {
        await page.locator("#reference").fill(reference);
        await page.locator("#submit-button").click();
        await page.locator(selector).waitFor();
        await page.screenshot({
          path: `${directory}/${name}-${width}.png`,
          fullPage: true,
        });
      }
      await page.close();
    }
  if (stage === "final") {
    for (const [width, scale] of [
      [320, 1],
      [720, 2],
    ]) {
      const page = await browser.newPage({
        viewport: { width, height: 900 },
        deviceScaleFactor: scale,
      });
      await page.route("**/api/support/session", (r) =>
        r.fulfill({ json: { username: "n3", role: "SUPPORT" } }),
      );
      await page.goto(`http://127.0.0.1:${server.address().port}/login`);
      await page.locator("#username").fill("n3");
      await page.locator("#password").fill("demo-change-me");
      await page.locator("#login-button").click();
      await page.locator("#dashboard").waitFor({ state: "visible" });
      observations.push({
        width,
        view: scale === 2 ? "zoom-equivalent-200-percent" : "reflow-320",
        deviceScaleFactor: scale,
        nativeBrowserZoomTested: false,
        overflow: await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
      });
      await page.close();
    }
  }
  await writeFile(
    `${directory}/measurements.json`,
    JSON.stringify({ stage, apiMocked: true, observations }, null, 2) + "\n",
  );
  console.log(JSON.stringify(observations.filter((x) => x.width === 390)));
} finally {
  if (browser) await browser.close();
  server.closeAllConnections();
  await new Promise((r) => server.close(r));
}
