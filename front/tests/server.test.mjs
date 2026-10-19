import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createAppServer } from "../server.mjs";

async function start(server, context) {
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(
    () =>
      new Promise((resolve) => {
        server.closeAllConnections();
        server.close(resolve);
      }),
  );
  return `http://127.0.0.1:${server.address().port}`;
}

test("serves only public assets with content security headers", async (context) => {
  const url = await start(createAppServer(), context);
  const response = await fetch(url);
  assert.equal(response.status, 200);
  assert.match(await response.text(), /Conciliación de pagos/);
  assert.match(
    response.headers.get("content-security-policy"),
    /frame-ancestors 'none'/,
  );
  for (const path of [
    "/package.json",
    "/server.mjs",
    "/.env",
    "/tests/api.test.mjs",
    "/nonexistent",
  ]) {
    assert.equal((await fetch(url + path)).status, 404);
  }
});

test("public assets disclose neither sample payment references nor demo credentials", async (context) => {
  const url = await start(createAppServer(), context);
  for (const path of [
    "/",
    "/src/app.js",
    "/src/api.js",
    "/src/presentation.js",
  ]) {
    const body = await (await fetch(url + path)).text();
    assert.doesNotMatch(body, /PAY-(AX91|BX17|CQ22|DK44)/);
    assert.doesNotMatch(body, /demo-change-me|use-demo|data-reference/);
  }
});

test("login endpoint forwards only GET and returns the verified identity", async (context) => {
  const upstream = await start(
    createServer((req, res) => {
      assert.equal(req.method, "GET");
      assert.equal(req.url, "/api/support/session");
      assert.equal(req.headers.authorization, "Basic test");
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ username: "n3", role: "SUPPORT" }));
    }),
    context,
  );
  const url = await start(createAppServer({ backend: upstream }), context);
  const response = await fetch(url + "/api/support/session", {
    headers: { Authorization: "Basic test" },
  });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).role, "SUPPORT");
  assert.equal(
    (await fetch(url + "/api/support/session", { method: "POST" })).status,
    405,
  );
  for (const route of ["/login", "/dashboard"])
    assert.equal((await fetch(url + route)).status, 200);
});

test("proxies only valid commands and forwards explicit auth without cookies", async (context) => {
  let received;
  const upstream = await start(
    createServer((req, res) => {
      received = {
        url: req.url,
        method: req.method,
        authorization: req.headers.authorization,
        cookie: req.headers.cookie,
      };
      res.setHeader("Content-Type", "application/json");
      res.end('{"outcome":"RESOLVED"}');
    }),
    context,
  );
  const url = await start(createAppServer({ backend: upstream }), context);
  const response = await fetch(
    url + "/api/support/payments/PAY-BX17/reconcile",
    {
      method: "POST",
      headers: { Authorization: "Basic test", Cookie: "not-forwarded=1" },
    },
  );
  assert.equal(response.status, 200);
  assert.deepEqual(received, {
    url: "/api/support/payments/PAY-BX17/reconcile",
    method: "POST",
    authorization: "Basic test",
    cookie: undefined,
  });
  assert.equal(
    (await fetch(url + "/api/support/payments/PAY-BX17/reconcile")).status,
    405,
  );
  assert.equal(
    (await fetch(url + "/api/not-allowed", { method: "POST" })).status,
    404,
  );
  assert.equal(
    (
      await fetch(url + "/api/support/payments/PAY-BX17/reconcile", {
        method: "POST",
        headers: { Origin: "https://untrusted.example" },
      })
    ).status,
    403,
  );
});

test("does not expose backend error bodies or browser authentication challenges", async (context) => {
  const upstream = await start(
    createServer((req, res) => {
      res.writeHead(401, { "WWW-Authenticate": 'Basic realm="test"' });
      res.end("SQL secret password");
    }),
    context,
  );
  const url = await start(createAppServer({ backend: upstream }), context);
  const response = await fetch(
    url + "/api/support/payments/PAY-BX17/reconcile",
    { method: "POST" },
  );
  assert.equal(response.status, 401);
  assert.equal(response.headers.get("www-authenticate"), null);
  assert.ok(!(await response.text()).includes("secret"));
});

test("upstream timeout is reported without retrying the financial command", async (context) => {
  let calls = 0;
  const upstream = await start(
    createServer(() => {
      calls++;
    }),
    context,
  );
  const url = await start(
    createAppServer({ backend: upstream, timeout: 30 }),
    context,
  );
  const response = await fetch(
    url + "/api/support/payments/PAY-BX17/reconcile",
    { method: "POST" },
  );
  assert.equal(response.status, 504);
  assert.equal(calls, 1);
});

test("rejects credentials and non-HTTP protocols in backend configuration", () => {
  for (const backend of [
    "file:///tmp",
    "http://user:pass@localhost:8080",
    "http://localhost:8080/not-an-origin",
  ]) {
    assert.throws(() => createAppServer({ backend }), /BACKEND_URL/);
  }
});

test("forwarded headers cannot relax the local HTTP origin check", async (context) => {
  let calls = 0;
  const upstream = await start(
    createServer((req, res) => {
      calls++;
      res.end("{}");
    }),
    context,
  );
  const url = await start(createAppServer({ backend: upstream }), context);
  for (const origin of [
    "https://untrusted.example",
    url.replace("http:", "https:"),
  ]) {
    const response = await fetch(
      url + "/api/support/payments/PAY-AX91/reconcile",
      {
        method: "POST",
        headers: {
          Origin: origin,
          "X-Forwarded-Proto": "https",
          "X-Forwarded-Host": new URL(origin).host,
        },
      },
    );
    assert.equal(response.status, 403);
    assert.equal(response.headers.get("cache-control"), "no-store");
  }
  assert.equal(calls, 0);
});

test("request bodies are rejected and a disconnected backend is sanitized", async (context) => {
  const closed = createServer();
  const unavailable = await start(closed, context);
  await new Promise((resolve) => closed.close(resolve));
  const url = await start(createAppServer({ backend: unavailable }), context);
  const body = await fetch(url + "/api/support/payments/PAY-AX91/reconcile", {
    method: "POST",
    body: "not-supported",
  });
  assert.equal(body.status, 400);
  const response = await fetch(
    url + "/api/support/payments/PAY-AX91/reconcile",
    { method: "POST" },
  );
  assert.equal(response.status, 502);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.doesNotMatch(await response.text(), /ECONNREFUSED|stack|password/);
});

test("timeout while consuming an upstream body remains a 504 with no retry", async (context) => {
  let calls = 0;
  const upstream = await start(
    createServer((req, res) => {
      calls++;
      res.writeHead(200, { "Content-Type": "application/json" });
      res.write("{");
    }),
    context,
  );
  const url = await start(
    createAppServer({ backend: upstream, timeout: 50 }),
    context,
  );
  const response = await fetch(
    url + "/api/support/payments/PAY-AX91/reconcile",
    { method: "POST" },
  );
  assert.equal(response.status, 504);
  assert.equal(calls, 1);
});
