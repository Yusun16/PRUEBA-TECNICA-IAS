import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const root = fileURLToPath(new URL(".", import.meta.url));
const assets = new Map([
  ["/", ["index.html", "text/html; charset=utf-8"]],
  ["/index.html", ["index.html", "text/html; charset=utf-8"]],
  ["/login", ["index.html", "text/html; charset=utf-8"]],
  ["/dashboard", ["index.html", "text/html; charset=utf-8"]],
  ["/styles.css", ["styles.css", "text/css; charset=utf-8"]],
  ["/favicon.svg", ["favicon.svg", "image/svg+xml"]],
  ["/src/app.js", ["src/app.js", "text/javascript; charset=utf-8"]],
  ["/src/api.js", ["src/api.js", "text/javascript; charset=utf-8"]],
  [
    "/src/presentation.js",
    ["src/presentation.js", "text/javascript; charset=utf-8"],
  ],
]);

function problem(res, status, title) {
  res.writeHead(status, {
    "Content-Type": "application/problem+json",
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify({ title, status }));
}

export function createAppServer({
  backend = process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
  timeout = 20000,
} = {}) {
  const upstream = new URL(backend);
  if (
    !["http:", "https:"].includes(upstream.protocol) ||
    upstream.username ||
    upstream.password ||
    upstream.pathname !== "/"
  ) {
    throw new Error(
      "BACKEND_URL debe ser un origen HTTP(S) sin credenciales ni ruta.",
    );
  }
  return createServer(async (req, res) => {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader(
      "Content-Security-Policy",
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; font-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'",
    );
    try {
      const url = new URL(req.url, "http://localhost");
      if (url.pathname.startsWith("/api/")) {
        const session = url.pathname === "/api/support/session";
        if (req.method !== (session ? "GET" : "POST"))
          return problem(res, 405, "METHOD_NOT_ALLOWED");
        if (
          (!session &&
            !/^\/api\/support\/payments\/[A-Za-z0-9_-]{1,40}\/reconcile$/.test(
              url.pathname,
            )) ||
          url.search
        )
          return problem(res, 404, "NOT_FOUND");
        if (
          req.headers.origin &&
          req.headers.origin !== `http://${req.headers.host}`
        )
          return problem(res, 403, "ORIGIN_NOT_ALLOWED");
        if (
          req.headers["transfer-encoding"] ||
          (req.headers["content-length"] &&
            req.headers["content-length"] !== "0")
        )
          return problem(res, 400, "BODY_NOT_SUPPORTED");
        const response = await fetch(new URL(url.pathname, upstream), {
          method: session ? "GET" : "POST",
          headers: {
            Accept: "application/json",
            ...(req.headers.authorization
              ? { Authorization: req.headers.authorization }
              : {}),
          },
          redirect: "error",
          signal: AbortSignal.timeout(timeout),
        });
        // Keep authentication in the explicit login form, not the browser's native Basic dialog.
        if (!response.ok)
          return problem(res, response.status, "RECONCILIATION_FAILED");
        const body = await response.text();
        res.writeHead(200, {
          "Content-Type": "application/json; charset=utf-8",
          "Cache-Control": "no-store",
        });
        res.end(body);
        return;
      }
      if (!["GET", "HEAD"].includes(req.method))
        return problem(res, 405, "METHOD_NOT_ALLOWED");
      const asset = assets.get(url.pathname);
      if (!asset) return problem(res, 404, "NOT_FOUND");
      const data = await readFile(resolve(root, asset[0]));
      res.writeHead(200, {
        "Content-Type": asset[1],
        "Cache-Control": "no-store",
      });
      res.end(req.method === "HEAD" ? undefined : data);
    } catch (error) {
      problem(
        res,
        error.name === "TimeoutError" ? 504 : 502,
        "BACKEND_UNAVAILABLE",
      );
    }
  });
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const port = Number(process.env.PORT ?? 5173);
  const server = createAppServer();
  server.listen(port, "127.0.0.1", () =>
    console.log(`PagoClaro frontend: http://127.0.0.1:${port}`),
  );
  server.on("error", (error) => {
    console.error(`No se pudo iniciar el frontend: ${error.code}`);
    process.exitCode = 1;
  });
}
