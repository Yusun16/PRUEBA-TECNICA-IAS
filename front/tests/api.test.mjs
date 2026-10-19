import test from "node:test";
import assert from "node:assert/strict";
import { reconcile, authenticate, ApiError } from "../src/api.js";
import {
  formatAmount,
  formatTime,
  isResult,
  reasonText,
} from "../src/presentation.js";

const credentials = { username: "n3", password: "test-secret" };

test("login validates identity with a read-only authenticated request", async () => {
  const identity = await authenticate(credentials, async (url, options) => {
    assert.equal(url, "/api/support/session");
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "omit");
    assert.equal(
      options.headers.Authorization,
      `Basic ${btoa("n3:test-secret")}`,
    );
    return Response.json({ username: "n3", role: "SUPPORT" });
  });
  assert.equal(identity.username, "n3");
});

test("login rejects invalid credentials, roles, network loss and invalid identity", async () => {
  await assert.rejects(authenticate(null), { status: 400 });
  for (const status of [401, 403, 503]) {
    await assert.rejects(
      authenticate(credentials, async () =>
        Response.json({ detail: "secret" }, { status }),
      ),
      (error) => {
        assert.equal(error.status, status);
        assert.ok(!error.message.includes("secret"));
        return true;
      },
    );
  }
  await assert.rejects(
    authenticate(credentials, async () => {
      throw new TypeError("network");
    }),
    /validar/,
  );
  await assert.rejects(
    authenticate(credentials, async () =>
      Response.json({ username: "n3", role: "VIEWER" }),
    ),
    /confirmar/,
  );
});

test("session timestamps use the Bogota time zone", () => {
  assert.match(formatTime(new Date("2026-10-19T12:00:00Z")), /07:00:00/);
});
const result = {
  reference: "PAY-BX17",
  outcome: "RESOLVED",
  reason: "APPROVED_RECONCILED",
  paymentStatus: "CONFIRMED",
  netDebitBefore: 178000,
  netDebitAfter: 89000,
  movementsCreated: 1,
  auditCreated: true,
};

test("sends an authenticated command and validates the returned contract", async () => {
  const data = await reconcile(
    "PAY-BX17",
    credentials,
    async (url, options) => {
      assert.equal(url, "/api/support/payments/PAY-BX17/reconcile");
      assert.equal(options.method, "POST");
      assert.equal(options.credentials, "omit");
      assert.equal(
        options.headers.Authorization,
        `Basic ${btoa("n3:test-secret")}`,
      );
      assert.equal(options.body, undefined);
      return Response.json(result);
    },
  );
  assert.deepEqual(data, {
    ...result,
    netDebitBefore: "178000",
    netDebitAfter: "89000",
  });
});

test("invalid references and missing credentials never send a command", async () => {
  const never = () => {
    throw new Error("must not call fetch");
  };
  await assert.rejects(reconcile("bad.reference", credentials, never), {
    status: 400,
  });
  await assert.rejects(reconcile("PAY-BX17", null, never), { status: 401 });
});

test("server errors are translated without exposing internal response details", async () => {
  for (const status of [400, 401, 403, 404, 409, 500, 502, 503, 504]) {
    await assert.rejects(
      reconcile("PAY-BX17", credentials, async () =>
        Response.json({ detail: "secret password SQL" }, { status }),
      ),
      (error) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.status, status);
        assert.ok(!error.message.includes("secret"));
        return true;
      },
    );
  }
});

test("network loss and timeout do not falsely report success or failure of the payment", async () => {
  await assert.rejects(
    reconcile("PAY-BX17", credentials, async () => {
      throw new TypeError("fetch failed");
    }),
    /podría haber terminado/,
  );
  await assert.rejects(
    reconcile("PAY-BX17", credentials, async () => {
      throw new DOMException("timeout", "TimeoutError");
    }),
    { status: 504 },
  );
});

test("rejects malformed results and responses for another payment", async () => {
  for (const data of [
    {},
    { ...result, outcome: "UNKNOWN" },
    { ...result, reference: "PAY-OTHER" },
    { ...result, movementsCreated: -1 },
  ]) {
    await assert.rejects(
      reconcile("PAY-BX17", credentials, async () => Response.json(data)),
      /inesperado/,
    );
  }
  await assert.rejects(
    reconcile(
      "PAY-BX17",
      credentials,
      async () => new Response("<html>error</html>"),
    ),
    /interpretarse/,
  );
});

test("presentation preserves decimals, checks contracts and avoids exposing unknown reason text", () => {
  assert.equal(formatAmount(89000.51), "89.000,51");
  assert.equal(isResult(result), true);
  assert.equal(isResult({ ...result, netDebitAfter: Infinity }), false);
  assert.equal(
    reasonText("<script>alert(1)</script>"),
    "Consulta la evidencia del pago con el equipo de soporte.",
  );
});

test("preserves NUMBER(18,2) decimal tokens without floating point rounding", async () => {
  const body =
    '{"reference":"PAY-BX17","outcome":"RESOLVED","reason":"APPROVED_RECONCILED","paymentStatus":"CONFIRMED","netDebitBefore":9999999999999999.99,"netDebitAfter":9999999999999999.99,"movementsCreated":1,"auditCreated":true}';
  const data = await reconcile(
    "PAY-BX17",
    credentials,
    async () => new Response(body),
  );
  assert.equal(data.netDebitAfter, "9999999999999999.99");
  assert.equal(formatAmount(data.netDebitAfter), "9.999.999.999.999.999,99");
});
