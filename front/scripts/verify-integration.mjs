import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { createWriteStream, existsSync, readdirSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import assert from "node:assert/strict";
import { chromium, expect } from "@playwright/test";

// Dedicated processes only. Never fall back to the user's ports or active database.
const front = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const back = resolve(front, "../back");
const jar = resolve(
  process.argv[2] ??
    join(back, ".tools/delivery-build/support-reconciliation-1.0.0-audit.jar"),
);
assert.ok(existsSync(jar), "Build the isolated delivery JAR first");
const logs = join(front, ".tools");
await mkdir(logs, { recursive: true });
const backendPort = 18084;
const frontendPort = 5175;
const backend = `http://127.0.0.1:${backendPort}`;
const origin = `http://127.0.0.1:${frontendPort}`;
for (const port of [backendPort, frontendPort]) {
  await new Promise((resolve, reject) => {
    const check = createServer();
    check.once("error", reject);
    check.listen(port, "127.0.0.1", () => check.close(resolve));
  });
}
const portable =
  existsSync(join(back, ".tools")) &&
  readdirSync(join(back, ".tools")).find((name) => name.startsWith("jdk-"));
const javaHome =
  process.env.JAVA_HOME ?? (portable && join(back, ".tools", portable));
const java = javaHome
  ? join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java")
  : "java";
const username = process.env.SUPPORT_USER ?? "n3";
const password = process.env.SUPPORT_PASSWORD ?? "demo-change-me";
const authorization = `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`;
const processes = [];
let browser;
function start(command, args, cwd, env, name) {
  const output = createWriteStream(join(logs, `${name}.log`));
  const child = spawn(command, args, {
    cwd,
    env: { ...process.env, ...env },
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.pipe(output);
  child.stderr.pipe(output);
  child.on("error", (error) => {
    child.launchError = error;
  });
  processes.push({ child, output });
  return child;
}
async function ready(url, child, expected) {
  for (let i = 0; i < 120; i++) {
    if (child.launchError) throw child.launchError;
    assert.equal(
      child.exitCode,
      null,
      "Isolated service exited before readiness",
    );
    try {
      if (
        (await fetch(url, { signal: AbortSignal.timeout(1000) })).status ===
        expected
      )
        return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Isolated service did not become ready: ${url}`);
}
const observations = [];
try {
  const api = start(
    java,
    [
      "-jar",
      jar,
      "--spring.profiles.active=local",
      "--debug=false",
      `--server.port=${backendPort}`,
      "--server.address=127.0.0.1",
      "--reconciliation.allow-duplicate-compensation=false",
    ],
    back,
    {},
    "delivery-real-backend",
  );
  await ready(`${backend}/api/support/session`, api, 401);
  const web = start(
    process.execPath,
    ["server.mjs"],
    front,
    { PORT: String(frontendPort), BACKEND_URL: backend },
    "delivery-real-front",
  );
  await ready(origin, web, 200);
  for (const [name, path, method, headers, status] of [
    ["unauthenticated", "/api/support/session", "GET", {}, 401],
    [
      "invalid-reference",
      "/api/support/payments/INVALID!/reconcile",
      "POST",
      { Authorization: authorization },
      400,
    ],
    [
      "missing-payment",
      "/api/support/payments/PAY-NOT-FOUND/reconcile",
      "POST",
      { Authorization: authorization },
      404,
    ],
  ]) {
    const response = await fetch(`${backend}${path}`, { method, headers });
    assert.equal(response.status, status);
    assert.match(
      response.headers.get("content-type"),
      /application\/problem\+json/,
    );
    const problem = await response.json();
    assert.equal(problem.status, status);
    assert.ok(
      !JSON.stringify(problem).match(/stackTrace|JdbcTemplate|SELECT /),
    );
    observations.push({ check: name, status, problemTitle: problem.title });
  }
  const identity = await fetch(`${origin}/api/support/session`, {
    headers: { Authorization: authorization },
  });
  assert.equal(identity.status, 200);
  assert.equal(identity.headers.get("cache-control"), "no-store");
  assert.equal(identity.headers.get("set-cookie"), null);
  assert.equal((await identity.json()).role, "SUPPORT");
  const chrome =
    process.env.CHROME_PATH ??
    [
      "C:/Program Files/Google/Chrome/Application/chrome.exe",
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    ].find(existsSync);
  browser = await chromium.launch(chrome ? { executablePath: chrome } : {});
  const page = await browser.newPage();
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto(`${origin}/dashboard`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator("#dashboard")).toBeHidden();
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(`${password}-incorrect`);
  await page.locator("#login-button").click();
  await expect(page.locator("#login-error")).toBeVisible();
  await expect(page.locator("#dashboard")).toBeHidden();
  await page.locator("#password").fill(password);
  await page.locator("#login-button").click();
  await expect(page.locator("#dashboard")).toBeVisible();
  const cases = [
    ["PAY-AX91", "RESOLVED", true, 125000],
    ["PAY-AX91", "ALREADY_CONSISTENT", false, 125000],
    ["PAY-BX17", "MANUAL_REVIEW", true, 178000],
    ["PAY-BX17", "MANUAL_REVIEW", false, 178000],
    ["PAY-CQ22", "MANUAL_REVIEW", true, 0],
    ["PAY-CQ22", "MANUAL_REVIEW", false, 0],
    ["PAY-DK44", "ALREADY_CONSISTENT", false, 31000],
    ["PAY-DK44", "ALREADY_CONSISTENT", false, 31000],
  ];
  for (const [reference, outcome, auditCreated, net] of cases) {
    await page.locator("#reference").fill(reference);
    const responsePromise = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/${reference}/reconcile`) &&
        response.request().method() === "POST",
    );
    await page.locator("#submit-button").click();
    const response = await responsePromise;
    assert.equal(response.status(), 200);
    const result = await response.json();
    assert.equal(result.outcome, outcome);
    assert.equal(result.auditCreated, auditCreated);
    assert.equal(result.movementsCreated, 0);
    assert.equal(result.netDebitAfter, net);
    await expect(page.locator("#result-label")).toHaveText(
      "OPERACIÓN COMPLETADA",
    );
    await expect(page.locator("#result-content")).toContainText(reference);
    await expect(page.locator("#result-content h3")).toContainText(
      outcome === "RESOLVED"
        ? "Pago conciliado"
        : outcome === "MANUAL_REVIEW"
          ? "Requiere revisión manual"
          : "El pago ya está consistente",
    );
    observations.push({ check: "real-browser-reconciliation", ...result });
  }
  const injectedPolicy = await fetch(
    `${backend}/api/support/payments/PAY-BX17/reconcile?allowDuplicateCompensation=true`,
    {
      method: "POST",
      headers: {
        Authorization: authorization,
        "Content-Type": "application/json",
        "X-Allow-Duplicate-Compensation": "true",
      },
      body: JSON.stringify({ allowDuplicateCompensation: true }),
    },
  );
  assert.equal(injectedPolicy.status, 200);
  const policyResult = await injectedPolicy.json();
  assert.equal(policyResult.outcome, "MANUAL_REVIEW");
  assert.equal(policyResult.movementsCreated, 0);
  assert.equal(policyResult.auditCreated, false);
  observations.push({
    check: "json-query-header-cannot-enable-compensation",
    ...policyResult,
  });
  assert.deepEqual(
    await page.evaluate(() => [localStorage.length, sessionStorage.length]),
    [0, 0],
  );
  await page.locator("#logout-button").click();
  await expect(page.locator("#dashboard")).toBeHidden();
  await page.reload();
  await expect(page.locator("#login-view")).toBeVisible();
  await expect(page.locator("#password")).toHaveValue("");
  assert.deepEqual(pageErrors, []);
  const evidence = {
    recordedAt: new Date().toISOString(),
    artifactSha256: createHash("sha256")
      .update(await readFile(jar))
      .digest("hex"),
    ports: { backend: backendPort, frontend: frontendPort },
    backendMocked: false,
    loginRejectedInvalidCredentials: true,
    browserCasesPassed: cases.length,
    logoutAndReloadClearedAccess: true,
    credentialsPersisted: false,
    pageErrors,
    observations,
  };
  await writeFile(
    join(logs, "delivery-real-integration.json"),
    JSON.stringify(evidence, null, 2) + "\n",
  );
  console.log(
    "PASS: real Java + Node proxy + Chrome, login, 8 reconciliations, retries, ProblemDetail and logout. No mocked API.",
  );
} finally {
  if (browser) await browser.close();
  for (const { child, output } of processes.reverse()) {
    if (child.exitCode === null && !child.killed) {
      await new Promise((resolve) => {
        child.once("exit", resolve);
        child.kill();
      });
    }
    output.end();
  }
}
