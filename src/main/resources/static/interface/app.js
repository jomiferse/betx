const statusBadge = document.querySelector("#statusBadge");
const statusMessage = document.querySelector("#statusMessage");
const availableBalance = document.querySelector("#availableBalance");
const manualConfirmation = document.querySelector("#manualConfirmation");
const activateButton = document.querySelector("#activateButton");
const pauseButton = document.querySelector("#pauseButton");
const activityList = document.querySelector("#activityList");
const activityCount = document.querySelector("#activityCount");

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error("request failed");
  }
  return response.json();
}

function statusLabel(status) {
  if (status === "ACTIVE") return "Activo";
  if (status === "NEEDS_ATTENTION") return "Necesita atencion";
  return "Pausado";
}

function statusClass(status) {
  if (status === "ACTIVE") return "status status-active";
  if (status === "NEEDS_ATTENTION") return "status status-attention";
  return "status status-paused";
}

function money(value) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR" }).format(Number(value));
}

function decimal(value) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("es-ES", { maximumFractionDigits: 2 }).format(Number(value));
}

function renderStatus(data) {
  statusBadge.textContent = statusLabel(data.status);
  statusBadge.className = statusClass(data.status);
  statusMessage.textContent = data.message || "Estado no disponible.";
  availableBalance.textContent = money(data.availableBalance);
  manualConfirmation.textContent = data.manualConfirmationEnabled ? "Activada" : "Desactivada";
}

function renderActivity(items) {
  activityCount.textContent = `${items.length} ${items.length === 1 ? "operacion" : "operaciones"}`;
  if (items.length === 0) {
    activityList.innerHTML = '<p class="empty">No hay actividad reciente.</p>';
    return;
  }
  activityList.innerHTML = items.map(item => `
    <div class="activity-row">
      <strong>${escapeHtml(item.event || "-")}</strong>
      <span>${escapeHtml(item.selection || "-")}</span>
      <span>${decimal(item.odds)}</span>
      <span>${money(item.amount)}</span>
      <span>${escapeHtml(item.statusLabel || "-")}</span>
    </div>
  `).join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function refresh() {
  try {
    const [status, activity] = await Promise.all([
      fetchJson("/api/interface/status"),
      fetchJson("/api/interface/activity")
    ]);
    renderStatus(status);
    renderActivity(activity);
  } catch (error) {
    renderStatus({
      status: "NEEDS_ATTENTION",
      message: "No se pudo actualizar BetX.",
      availableBalance: null,
      manualConfirmationEnabled: false
    });
  }
}

async function postAction(url) {
  activateButton.disabled = true;
  pauseButton.disabled = true;
  try {
    const status = await fetchJson(url, { method: "POST" });
    renderStatus(status);
    await refresh();
  } finally {
    activateButton.disabled = false;
    pauseButton.disabled = false;
  }
}

activateButton.addEventListener("click", () => postAction("/api/interface/activate"));
pauseButton.addEventListener("click", () => postAction("/api/interface/pause"));

refresh();
setInterval(refresh, 5000);
