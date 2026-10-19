import { defineConfig } from "@playwright/test";
import { existsSync } from "node:fs";

const chrome =
  process.env.CHROME_PATH ??
  [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  ].find((path) => existsSync(path));

export default defineConfig({
  testDir: "./tests/browser",
  fullyParallel: true,
  workers: 2,
  timeout: 20000,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:5174",
    viewport: { width: 1440, height: 1080 },
    launchOptions: chrome ? { executablePath: chrome } : {},
    trace: "retain-on-failure",
  },
  webServer: {
    command: "node server.mjs",
    url: "http://127.0.0.1:5174",
    env: { PORT: "5174" },
    reuseExistingServer: false,
  },
});
