export const referencePattern = /^[A-Za-z0-9_-]{1,40}$/;

export const outcomes = {
  RESOLVED: {
    label: "Pago conciliado",
    short: "Corregido",
    tone: "success",
    description: "La corrección se completó y quedó registrada.",
  },
  ALREADY_CONSISTENT: {
    label: "El pago ya está consistente",
    short: "Sin cambios",
    tone: "neutral",
    description: "No fue necesario crear movimientos ni modificar el pago.",
  },
  MANUAL_REVIEW: {
    label: "Requiere revisión manual",
    short: "Revisión manual",
    tone: "warning",
    description:
      "Se conservó el estado y el historial contable del pago. Revisa la evidencia antes de continuar.",
  },
};

const reasons = {
  APPROVED_RECONCILED:
    "La evidencia de aprobación y el saldo contable quedaron conciliados.",
  DECLINED_WITHOUT_LEDGER:
    "El proveedor rechazó el pago y no existe historial contable. Se actualizó el estado.",
  NO_CHANGES_REQUIRED:
    "El estado y los movimientos coinciden con la evidencia disponible.",
  MISSING_PROVIDER_EVIDENCE:
    "No hay eventos del proveedor suficientes para decidir con seguridad.",
  CONTRADICTORY_PROVIDER_EVIDENCE:
    "Hay estados contradictorios o desconocidos en los eventos del proveedor.",
  INVALID_PAYMENT: "El pago tiene un estado o importe que requiere validación.",
  INVALID_LEDGER: "El historial contiene movimientos o importes no válidos.",
  AMBIGUOUS_DEBIT:
    "Existen débitos cuyo importe o relación no permite una corrección automática segura.",
  INVALID_REVERSAL:
    "Un reverso no corresponde a un débito válido del mismo pago o ya fue aplicado.",
  DECLINED_WITH_LEDGER_HISTORY:
    "El proveedor rechazó el pago, pero existe historial contable que debe revisarse.",
  APPROVED_WITH_REVERSED_HISTORY:
    "El pago aprobado ya tiene su historial revertido. Podría corresponder a una devolución.",
};

export const paymentStatuses = {
  PENDING: "Pendiente",
  CONFIRMED: "Confirmado",
  REJECTED: "Rechazado",
};
export function reasonText(code) {
  return (
    reasons[code] ?? "Consulta la evidencia del pago con el equipo de soporte."
  );
}
export function formatAmount(amount) {
  return new Intl.NumberFormat("es-CO", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}
export function formatTime(date) {
  return new Intl.DateTimeFormat("es-CO", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZone: "America/Bogota",
  }).format(date);
}
export function isResult(data) {
  const isAmount = (value) =>
    (typeof value === "number" && Number.isFinite(value)) ||
    (typeof value === "string" &&
      value.length <= 32 &&
      /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:e[+-]?\d+)?$/i.test(value) &&
      Number.isFinite(Number(value)));
  return Boolean(
    data &&
      typeof data.reference === "string" &&
      outcomes[data.outcome] &&
      paymentStatuses[data.paymentStatus] &&
      typeof data.reason === "string" &&
      isAmount(data.netDebitBefore) &&
      isAmount(data.netDebitAfter) &&
      Number.isInteger(data.movementsCreated) &&
      data.movementsCreated >= 0 &&
      typeof data.auditCreated === "boolean",
  );
}
