import { reconcile } from "./api.js";
import {
  referencePattern,
  outcomes,
  reasonText,
  paymentStatuses,
  formatAmount,
  formatTime,
} from "./presentation.js";

const $ = (selector) => document.querySelector(selector);
const state = {
  credentials: null,
  busy: false,
  history: [],
  total: 0,
  resolved: 0,
  manual: 0,
  lastReference: null,
};
const dialog = $("#connection-dialog");

function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function openConnection() {
  $("#username").value = state.credentials?.username ?? "n3";
  $("#password").value = "";
  $("#connection-error").hidden = true;
  $("#disconnect").hidden = !state.credentials;
  dialog.showModal();
}

function setCredentials(credentials) {
  state.credentials = credentials;
  $("#connection-label").textContent = credentials
    ? "Acceso configurado"
    : "Configurar acceso";
  $("#operator-name").textContent = credentials
    ? credentials.username
    : "Sin credenciales";
  $("#connection-dot").classList.toggle("configured", Boolean(credentials));
  $("#password").value = "";
}

function changeView(view) {
  $("#reconcile-view").hidden = view !== "reconcile";
  $("#activity-view").hidden = view !== "activity";
  $("#breadcrumb-title").textContent =
    view === "activity" ? "Actividad de sesión" : "Conciliación de pagos";
  for (const button of document.querySelectorAll("[data-view]")) {
    const active = button.dataset.view === view;
    button.classList.toggle("active", active);
    if (active) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  }
}

function selectReference(reference) {
  if (state.busy) return;
  $("#reference").value = reference;
  $("#form-error").hidden = true;
  $("#reference").removeAttribute("aria-invalid");
  syncSelection();
  changeView("reconcile");
  $("#reference").focus();
}

function syncSelection() {
  const reference = $("#reference").value.trim();
  document
    .querySelectorAll("[data-reference]")
    .forEach((button) =>
      button.classList.toggle(
        "selected",
        button.dataset.reference === reference,
      ),
    );
  const old = $("#stale-note");
  if (old) old.remove();
  if (state.lastReference && reference !== state.lastReference && !state.busy) {
    const note = node(
      "p",
      "result-stale",
      `El resultado visible corresponde a ${state.lastReference}. Concilia la nueva referencia para actualizarlo.`,
    );
    note.id = "stale-note";
    $("#result-content").append(note);
  }
}

function setBusy(busy) {
  state.busy = busy;
  $("#submit-button").disabled = busy;
  $("#reference").disabled = busy;
  $("#connection-open").disabled = busy;
  document.querySelectorAll("[data-reference]").forEach((button) => {
    button.disabled = busy;
  });
  $("#submit-label").textContent = busy ? "Conciliando…" : "Conciliar pago";
  $("#submit-icon").textContent = busy ? "···" : "→";
  $("#result-region").setAttribute("aria-busy", String(busy));
}

function showLoading(reference) {
  $("#result-label").textContent = "EN PROCESO";
  const region = $("#result-content");
  region.className = "loading-state";
  region.replaceChildren(
    node("span", "spinner"),
    node("p", "", `Conciliando ${reference}…`),
    node("small", "", "Validando evidencia y movimientos contables."),
  );
}

function showResult(result, date) {
  const outcome = outcomes[result.outcome];
  $("#result-label").textContent = "OPERACIÓN COMPLETADA";
  const content = $("#result-content");
  content.className = "result-content";
  content.replaceChildren();
  const banner = node("div", `result-status ${outcome.tone}`);
  const title = node("h3");
  title.append(
    node(
      "span",
      "status-symbol",
      result.outcome === "MANUAL_REVIEW" ? "!" : "✓",
    ),
    document.createTextNode(outcome.label),
  );
  banner.append(title, node("p", "", outcome.description));
  const reference = node("div", "result-reference");
  reference.append(
    node("span", "", result.reference),
    node(
      "span",
      `badge ${outcome.tone}`,
      paymentStatuses[result.paymentStatus],
    ),
  );
  const amounts = node("div", "amount-comparison");
  for (const [label, amount] of [
    ["Débito neto anterior", result.netDebitBefore],
    ["Débito neto final", result.netDebitAfter],
  ]) {
    const item = node("div", "amount-item");
    item.append(
      node("small", "", label),
      node("strong", "", formatAmount(amount)),
    );
    amounts.append(item);
  }
  const facts = node("dl", "result-facts");
  for (const [label, value] of [
    ["Movimientos creados", String(result.movementsCreated)],
    [
      "Nueva auditoría",
      result.auditCreated ? "Sí, registrada" : "Sin registro adicional",
    ],
  ]) {
    const row = node("div");
    row.append(node("dt", "", label), node("dd", "", value));
    facts.append(row);
  }
  content.append(
    banner,
    reference,
    amounts,
    node("p", "amount-unit-note", "Importes en la unidad monetaria del pago."),
    facts,
    node("p", "result-reason", reasonText(result.reason)),
    node("p", "result-time", `Respuesta recibida a las ${formatTime(date)}`),
  );
}

function showError(reference, error) {
  $("#result-label").textContent = "SIN RESULTADO CONFIRMADO";
  const content = $("#result-content");
  content.className = "result-content";
  content.replaceChildren();
  const banner = node("div", "result-status result-error");
  banner.append(
    node("h3", "", "No pudimos confirmar el resultado"),
    node("p", "", error.message),
  );
  content.append(
    banner,
    node("div", "result-reference", reference),
    node(
      "p",
      "result-reason",
      "No asumas que hubo una corrección. Si la respuesta se perdió, verifica y reintenta la misma referencia.",
    ),
  );
}

function historyTable(entries) {
  const scroll = node("div", "table-scroll");
  const table = node("table");
  const head = node("thead");
  const headRow = node("tr");
  ["REFERENCIA", "RESULTADO", "MOVIMIENTOS", "HORA"].forEach((label) => {
    const th = node("th", "", label);
    th.scope = "col";
    headRow.append(th);
  });
  head.append(headRow);
  const body = node("tbody");
  for (const entry of entries) {
    const row = node("tr");
    const cell = node("td");
    const button = node("button", "activity-ref", entry.reference);
    button.type = "button";
    button.title = "Seleccionar esta referencia sin ejecutar";
    button.addEventListener("click", () => selectReference(entry.reference));
    cell.append(button);
    const resultCell = node("td");
    const outcome = entry.result
      ? outcomes[entry.result.outcome]
      : { short: "Sin respuesta", tone: "error" };
    resultCell.append(node("span", `badge ${outcome.tone}`, outcome.short));
    row.append(
      cell,
      resultCell,
      node(
        "td",
        "",
        entry.result ? String(entry.result.movementsCreated) : "—",
      ),
      node("td", "", formatTime(entry.date)),
    );
    body.append(row);
  }
  table.append(head, body);
  scroll.append(table);
  return scroll;
}

function updateActivity() {
  $("#metric-total").textContent = state.total;
  $("#metric-resolved").textContent = state.resolved;
  $("#metric-manual").textContent = state.manual;
  $("#nav-count").textContent = state.total;
  for (const [id, entries] of [
    ["#recent-activity", state.history.slice(0, 4)],
    ["#all-activity", state.history],
  ]) {
    const container = $(id);
    container.classList.add("has-rows");
    container.replaceChildren(historyTable(entries));
  }
}

$("#reconcile-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (state.busy) return;
  const reference = $("#reference").value.trim();
  if (!referencePattern.test(reference)) {
    $("#form-error").textContent =
      "Ingresa una referencia válida: hasta 40 letras, números, guiones o guiones bajos.";
    $("#form-error").hidden = false;
    $("#reference").setAttribute("aria-invalid", "true");
    $("#reference").focus();
    return;
  }
  if (!state.credentials) {
    openConnection();
    return;
  }
  $("#form-error").hidden = true;
  $("#reference").removeAttribute("aria-invalid");
  setBusy(true);
  showLoading(reference);
  let entry;
  try {
    const result = await reconcile(reference, state.credentials);
    entry = { reference, result, date: new Date() };
    showResult(result, entry.date);
    if (result.outcome === "RESOLVED") state.resolved++;
    if (result.outcome === "MANUAL_REVIEW") state.manual++;
  } catch (error) {
    entry = { reference, date: new Date() };
    showError(reference, error);
    if (error.status === 401) setCredentials(null);
  } finally {
    state.lastReference = reference;
    state.total++;
    state.history.unshift(entry);
    state.history = state.history.slice(0, 30);
    updateActivity();
    setBusy(false);
  }
});

$("#connection-open").addEventListener("click", openConnection);
$("#connection-close").addEventListener("click", () => dialog.close());
$("#connection-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const username = $("#username").value.trim();
  const password = $("#password").value;
  if (!username || username.includes(":") || !password) {
    $("#connection-error").textContent =
      "Ingresa un usuario sin dos puntos y una contraseña.";
    $("#connection-error").hidden = false;
    return;
  }
  setCredentials({ username, password });
  dialog.close();
});
$("#use-demo").addEventListener("click", () => {
  $("#username").value = "n3";
  $("#password").value = "demo-change-me";
});
$("#disconnect").addEventListener("click", () => {
  setCredentials(null);
  dialog.close();
});
dialog.addEventListener("close", () => {
  $("#password").value = "";
});
document
  .querySelectorAll("[data-view]")
  .forEach((button) =>
    button.addEventListener("click", () => changeView(button.dataset.view)),
  );
document
  .querySelectorAll("[data-reference]")
  .forEach((button) =>
    button.addEventListener("click", () =>
      selectReference(button.dataset.reference),
    ),
  );
$("#reference").addEventListener("input", syncSelection);
$("#show-activity").addEventListener("click", () => changeView("activity"));
$("#back-to-reconcile").addEventListener("click", () =>
  changeView("reconcile"),
);
$("#footer-date").textContent = new Intl.DateTimeFormat("es-CO", {
  day: "numeric",
  month: "long",
  year: "numeric",
  timeZone: "America/Bogota",
}).format(new Date());
