import { isResult, referencePattern } from "./presentation.js";

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const errors = {
  400: "La referencia no es válida. Usa hasta 40 letras, números, guiones o guiones bajos.",
  401: "Las credenciales no son correctas. Revisa la conexión de soporte.",
  403: "Tu usuario no tiene permisos de soporte para conciliar pagos.",
  404: "No encontramos un pago con esa referencia. Verifica que esté escrita correctamente.",
  409: "Otra operación está trabajando sobre este pago. Espera un momento y vuelve a intentarlo.",
  502: "No se pudo conectar con el backend. Comprueba que esté iniciado y vuelve a intentarlo.",
  503: "No fue posible completar la operación. Verifica el estado del pago antes de reintentar.",
  504: "La respuesta tardó demasiado. El pago podría haberse procesado: verifica y reintenta la misma referencia.",
};

function basicAuth(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  return `Basic ${btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join(""))}`;
}

export async function authenticate(credentials, fetcher = fetch) {
  if (
    !credentials?.username ||
    !credentials.password ||
    credentials.username.includes(":")
  ) {
    throw new ApiError("Ingresa un usuario válido y una contraseña.", 400);
  }
  let response;
  try {
    response = await fetcher("/api/support/session", {
      method: "GET",
      headers: {
        Authorization: basicAuth(credentials.username, credentials.password),
        Accept: "application/json",
      },
      credentials: "omit",
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    });
  } catch {
    throw new ApiError(
      "No pudimos validar el acceso. Comprueba la conexión con el servidor e inténtalo de nuevo.",
    );
  }
  if (!response.ok) {
    throw new ApiError(
      response.status === 401
        ? "Usuario o contraseña incorrectos."
        : response.status === 403
          ? "Tu usuario no tiene acceso al panel de soporte."
          : "No pudimos validar el acceso. Comprueba que el backend esté iniciado.",
      response.status,
    );
  }
  let identity;
  try {
    identity = await response.json();
  } catch {
    throw new ApiError("El servidor no pudo confirmar tu acceso.");
  }
  if (
    typeof identity?.username !== "string" ||
    !identity.username ||
    identity.role !== "SUPPORT"
  ) {
    throw new ApiError("El servidor no pudo confirmar tu acceso.");
  }
  return identity;
}

export async function reconcile(reference, credentials, fetcher = fetch) {
  if (!referencePattern.test(reference)) throw new ApiError(errors[400], 400);
  if (
    !credentials?.username ||
    !credentials.password ||
    credentials.username.includes(":")
  ) {
    throw new ApiError(
      "Configura el usuario y la contraseña de soporte antes de continuar.",
      401,
    );
  }
  let response;
  try {
    response = await fetcher(
      `/api/support/payments/${encodeURIComponent(reference)}/reconcile`,
      {
        method: "POST",
        headers: {
          Authorization: basicAuth(credentials.username, credentials.password),
          Accept: "application/json",
        },
        credentials: "omit",
        cache: "no-store",
        signal: AbortSignal.timeout(25000),
      },
    );
  } catch (error) {
    if (error.name === "TimeoutError" || error.name === "AbortError")
      throw new ApiError(errors[504], 504);
    throw new ApiError(
      "Se perdió la conexión. La operación podría haber terminado; verifica y reintenta la misma referencia.",
    );
  }
  if (!response.ok)
    throw new ApiError(
      errors[response.status] ??
        "No fue posible completar la conciliación. Consulta al equipo de soporte.",
      response.status,
    );
  let data;
  try {
    // Keep the original decimal token. Monetary values must not be rounded through a JS double.
    data = JSON.parse(await response.text(), (key, value, context) => {
      if (
        ["netDebitBefore", "netDebitAfter"].includes(key) &&
        typeof value === "number"
      ) {
        if (context?.source) return context.source;
        if (Math.abs(value) > Number.MAX_SAFE_INTEGER / 100)
          throw new Error("Browser lacks lossless JSON support");
        return String(value);
      }
      return value;
    });
  } catch {
    throw new ApiError(
      "La respuesta no pudo interpretarse. Verifica el pago antes de reintentar.",
    );
  }
  if (!isResult(data) || data.reference !== reference)
    throw new ApiError(
      "El servidor devolvió un resultado inesperado. Verifica el pago antes de reintentar.",
    );
  return data;
}
