import { reconcile, authenticate } from "./api.js";
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
const initialResult = $("#result-content").cloneNode(true);

function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function signOut(message = "") {
  state.credentials = null;
  state.history = [];
  state.total = 0;
  state.resolved = 0;
  state.manual = 0;
  state.lastReference = null;
  $("#reference").value = "";
  $("#reference").removeAttribute("aria-invalid");
  $("#form-error").hidden = true;
  $("#result-content").replaceWith(initialResult.cloneNode(true));
  $("#result-label").textContent = "SIN EJECUTAR";
  updateActivity();
  $("#dashboard").hidden = true;
  $("#login-view").hidden = false;
  $("#username").value = "";
  $("#password").value = "";
  $("#password").type = "password";
  $("#password-toggle").textContent = "Mostrar";
  $("#password-toggle").setAttribute("aria-pressed", "false");
  $("#password-toggle").setAttribute("aria-label", "Mostrar contrase\u00f1a");
  $("#operator-name").textContent = "";
  $("#login-error").textContent = message;
  $("#login-error").hidden = !message;
  history.replaceState(null, "", "/login");
  document.title = "PagoClaro | Iniciar sesi\u00f3n";
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
  syncResultReference();
  changeView("reconcile");
  $("#reference").focus();
}

function syncResultReference() {
  const reference = $("#reference").value.trim();
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
  $("#logout-button").disabled = busy;
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
    signOut();
    return;
  }
  $("#form-error").hidden = true;
  $("#reference").removeAttribute("aria-invalid");
  setBusy(true);
  showLoading(reference);
  let entry;
  let accessLost = false;
  try {
    const result = await reconcile(reference, state.credentials);
    entry = { reference, result, date: new Date() };
    showResult(result, entry.date);
    if (result.outcome === "RESOLVED") state.resolved++;
    if (result.outcome === "MANUAL_REVIEW") state.manual++;
  } catch (error) {
    entry = { reference, date: new Date() };
    showError(reference, error);
    if (error.status === 401 || error.status === 403) {
      accessLost = true;
      signOut("Tu acceso ya no es v\u00e1lido. Inicia sesi\u00f3n de nuevo.");
    }
  } finally {
    if (!accessLost) {
      state.lastReference = reference;
      state.total++;
      state.history.unshift(entry);
      state.history = state.history.slice(0, 30);
      updateActivity();
    }
    setBusy(false);
  }
});

$("#login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if ($("#login-button").disabled) return;
  const credentials = {
    username: $("#username").value.trim(),
    password: $("#password").value,
  };
  $("#login-error").hidden = true;
  $("#login-button").disabled = true;
  $("#username").disabled = true;
  $("#password").disabled = true;
  $("#password-toggle").disabled = true;
  $("#login-button-label").textContent = "Validando acceso...";
  try {
    const identity = await authenticate(credentials);
    state.credentials = credentials;
    $("#operator-name").textContent = identity.username;
    $("#connection-dot").classList.add("configured");
    $("#password").value = "";
    $("#login-view").hidden = true;
    $("#dashboard").hidden = false;
    changeView("reconcile");
    history.replaceState(null, "", "/dashboard");
    document.title = "PagoClaro | Conciliaci\u00f3n de pagos";
    $("#main").focus();
  } catch (error) {
    $("#password").value = "";
    $("#login-error").textContent = error.message;
    $("#login-error").hidden = false;
  } finally {
    $("#login-button").disabled = false;
    $("#username").disabled = false;
    $("#password").disabled = false;
    $("#password-toggle").disabled = false;
    $("#login-button-label").textContent = "Iniciar sesi\u00f3n";
  }
});
$("#logout-button").addEventListener("click", () => {
  if (!state.busy) {
    signOut();
    $("#username").focus();
  }
});
$("#password-toggle").addEventListener("click", () => {
  const visible = $("#password").type === "password";
  $("#password").type = visible ? "text" : "password";
  $("#password-toggle").textContent = visible ? "Ocultar" : "Mostrar";
  $("#password-toggle").setAttribute("aria-pressed", String(visible));
  $("#password-toggle").setAttribute(
    "aria-label",
    visible ? "Ocultar contrase\u00f1a" : "Mostrar contrase\u00f1a",
  );
});
window.addEventListener("popstate", () =>
  history.replaceState(null, "", state.credentials ? "/dashboard" : "/login"),
);
signOut();

document
  .querySelectorAll("[data-view]")
  .forEach((button) =>
    button.addEventListener("click", () => changeView(button.dataset.view)),
  );
$("#reference").addEventListener("input", syncResultReference);
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
