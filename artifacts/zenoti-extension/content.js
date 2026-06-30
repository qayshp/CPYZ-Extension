(() => {
  if (document.getElementById("zenoti-inspector-panel")) return;

  let currentToken = null;
  let tokenCapturedAt = null;
  let panelVisible = false;
  let tickInterval = null;

  const TOKEN_HEADER_KEYS = ["authorization", "x-session-token", "x-auth-token", "x-zenoti-token"];

  function parseJwtExpiry(token) {
    try {
      const parts = token.split(".");
      if (parts.length !== 3) return null;
      const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
      return payload.exp ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }

  function formatTimeRemaining(expiryMs) {
    const diff = expiryMs - Date.now();
    if (diff <= 0) return { label: "EXPIRED", cls: "expired" };
    const s = Math.floor(diff / 1000);
    const m = Math.floor(s / 60);
    const h = Math.floor(m / 60);
    if (h > 0) return { label: `${h}h ${m % 60}m remaining`, cls: "ok" };
    if (m > 5) return { label: `${m}m ${s % 60}s remaining`, cls: "ok" };
    if (m > 0) return { label: `${m}m ${s % 60}s remaining`, cls: "warn" };
    return { label: `${s}s remaining`, cls: "danger" };
  }

  function formatDate(ms) {
    if (!ms) return "—";
    return new Date(ms).toLocaleString();
  }

  function truncate(str, n = 40) {
    if (!str) return "—";
    return str.length > n ? str.slice(0, n) + "…" : str;
  }

  const PLACEHOLDER_QUERIES = [
    { id: "q1", label: "Current User / Profile", endpoint: "/v1/users/me" },
    { id: "q2", label: "Centers / Locations", endpoint: "/v1/centers" },
    { id: "q3", label: "Upcoming Appointments", endpoint: "/v1/appointments?status=upcoming&limit=5" },
    { id: "q4", label: "Memberships", endpoint: "/v1/memberships" },
    { id: "q5", label: "Classes / Schedule", endpoint: "/v1/classes?limit=5" },
  ];

  function buildPanel() {
    const panel = document.createElement("div");
    panel.id = "zenoti-inspector-panel";
    panel.innerHTML = `
      <div id="zip-header">
        <span id="zip-title">Zenoti Inspector</span>
        <div id="zip-header-actions">
          <button id="zip-minimize" title="Minimize">−</button>
          <button id="zip-close" title="Close">✕</button>
        </div>
      </div>
      <div id="zip-body">
        <section class="zip-section">
          <div class="zip-section-title">Session Token</div>
          <div id="zip-token-value" class="zip-mono zip-token-box">Waiting for a request…</div>
          <div id="zip-token-meta">
            <div class="zip-meta-row">
              <span class="zip-label">Captured</span>
              <span id="zip-captured-at">—</span>
            </div>
            <div class="zip-meta-row">
              <span class="zip-label">Expires</span>
              <span id="zip-expiry-abs">—</span>
            </div>
            <div class="zip-meta-row">
              <span class="zip-label">Time left</span>
              <span id="zip-expiry-rel" class="zip-badge">—</span>
            </div>
          </div>
          <div id="zip-token-actions">
            <button class="zip-btn" id="zip-copy-btn" disabled>Copy Token</button>
            <button class="zip-btn zip-btn-secondary" id="zip-clear-btn" disabled>Clear</button>
          </div>
        </section>

        <section class="zip-section">
          <div class="zip-section-title">API Queries
            <span class="zip-hint">Click to run against the live API</span>
          </div>
          <div id="zip-queries">
            ${PLACEHOLDER_QUERIES.map((q) => `
              <div class="zip-query-item" id="zip-q-${q.id}">
                <div class="zip-query-header">
                  <span class="zip-query-label">${q.label}</span>
                  <span class="zip-query-endpoint zip-mono">${q.endpoint}</span>
                  <button class="zip-run-btn" data-id="${q.id}" data-endpoint="${q.endpoint}" disabled>Run</button>
                </div>
                <div class="zip-query-result zip-mono" id="zip-result-${q.id}">—</div>
              </div>
            `).join("")}
          </div>
        </section>

        <div id="zip-footer">
          <span id="zip-status">No token captured yet. Browse the site to trigger a request.</span>
        </div>
      </div>
    `;
    document.body.appendChild(panel);

    makeDraggable(panel, document.getElementById("zip-header"));

    document.getElementById("zip-close").addEventListener("click", () => {
      panel.remove();
      clearInterval(tickInterval);
    });

    document.getElementById("zip-minimize").addEventListener("click", () => {
      const body = document.getElementById("zip-body");
      const isMin = body.style.display === "none";
      body.style.display = isMin ? "" : "none";
      document.getElementById("zip-minimize").textContent = isMin ? "−" : "+";
    });

    document.getElementById("zip-copy-btn").addEventListener("click", () => {
      if (!currentToken) return;
      navigator.clipboard.writeText(currentToken).then(() => setStatus("Token copied to clipboard."));
    });

    document.getElementById("zip-clear-btn").addEventListener("click", () => {
      currentToken = null;
      tokenCapturedAt = null;
      renderTokenUI();
      setStatus("Token cleared.");
      chrome.storage.session.remove(["token", "tokenCapturedAt"]);
    });

    document.querySelectorAll(".zip-run-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        const { id, endpoint } = e.target.dataset;
        runQuery(id, endpoint);
      });
    });
  }

  function renderTokenUI() {
    const box = document.getElementById("zip-token-value");
    const capturedEl = document.getElementById("zip-captured-at");
    const expiryAbs = document.getElementById("zip-expiry-abs");
    const expiryRel = document.getElementById("zip-expiry-rel");
    const copyBtn = document.getElementById("zip-copy-btn");
    const clearBtn = document.getElementById("zip-clear-btn");

    if (!currentToken) {
      box.textContent = "Waiting for a request…";
      capturedEl.textContent = "—";
      expiryAbs.textContent = "—";
      expiryRel.textContent = "—";
      expiryRel.className = "zip-badge";
      copyBtn.disabled = true;
      clearBtn.disabled = true;
      document.querySelectorAll(".zip-run-btn").forEach((b) => (b.disabled = true));
      return;
    }

    box.textContent = truncate(currentToken, 60);
    box.title = currentToken;
    capturedEl.textContent = formatDate(tokenCapturedAt);
    copyBtn.disabled = false;
    clearBtn.disabled = false;
    document.querySelectorAll(".zip-run-btn").forEach((b) => (b.disabled = false));

    const expiry = parseJwtExpiry(currentToken);
    if (expiry) {
      expiryAbs.textContent = formatDate(expiry);
      updateExpiryBadge(expiry, expiryRel);
    } else {
      expiryAbs.textContent = "Non-JWT token";
      expiryRel.textContent = "Unknown";
      expiryRel.className = "zip-badge";
    }
  }

  function updateExpiryBadge(expiryMs, el) {
    const { label, cls } = formatTimeRemaining(expiryMs);
    el.textContent = label;
    el.className = `zip-badge zip-badge-${cls}`;
  }

  function startTicker() {
    clearInterval(tickInterval);
    tickInterval = setInterval(() => {
      if (!currentToken) return;
      const expiry = parseJwtExpiry(currentToken);
      if (!expiry) return;
      const el = document.getElementById("zip-expiry-rel");
      if (el) updateExpiryBadge(expiry, el);
    }, 1000);
  }

  function setStatus(msg) {
    const el = document.getElementById("zip-status");
    if (el) el.textContent = msg;
  }

  function runQuery(id, endpoint) {
    if (!currentToken) return;
    const resultEl = document.getElementById(`zip-result-${id}`);
    const btn = document.querySelector(`.zip-run-btn[data-id="${id}"]`);
    resultEl.textContent = "Loading…";
    resultEl.className = "zip-query-result zip-mono zip-loading";
    btn.disabled = true;

    chrome.runtime.sendMessage({ type: "FETCH_API", endpoint, token: currentToken }, (resp) => {
      btn.disabled = false;
      if (!resp) {
        resultEl.textContent = "Error: No response from background.";
        resultEl.className = "zip-query-result zip-mono zip-error";
        return;
      }
      if (resp.ok) {
        resultEl.textContent = JSON.stringify(resp.data, null, 2);
        resultEl.className = "zip-query-result zip-mono zip-success";
      } else {
        resultEl.textContent = `Error: ${resp.error}`;
        resultEl.className = "zip-query-result zip-mono zip-error";
      }
    });
  }

  function makeDraggable(panel, handle) {
    let startX, startY, startLeft, startTop;
    handle.style.cursor = "grab";

    handle.addEventListener("mousedown", (e) => {
      if (e.target.tagName === "BUTTON") return;
      startX = e.clientX;
      startY = e.clientY;
      const rect = panel.getBoundingClientRect();
      startLeft = rect.left;
      startTop = rect.top;
      panel.style.right = "auto";
      panel.style.bottom = "auto";
      panel.style.left = startLeft + "px";
      panel.style.top = startTop + "px";
      handle.style.cursor = "grabbing";

      const onMove = (e) => {
        panel.style.left = Math.max(0, startLeft + e.clientX - startX) + "px";
        panel.style.top = Math.max(0, startTop + e.clientY - startY) + "px";
      };
      const onUp = () => {
        handle.style.cursor = "grab";
        document.removeEventListener("mousemove", onMove);
        document.removeEventListener("mouseup", onUp);
      };
      document.addEventListener("mousemove", onMove);
      document.addEventListener("mouseup", onUp);
    });
  }

  function applyToken(token, capturedAtMs) {
    currentToken = token;
    tokenCapturedAt = capturedAtMs || Date.now();
    renderTokenUI();
    setStatus("Token captured from live request.");
  }

  chrome.runtime.sendMessage({ type: "GET_TOKEN" }, (resp) => {
    if (resp && resp.token) {
      applyToken(resp.token, resp.tokenCapturedAt);
    }
  });

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === "TOKEN_UPDATED" && msg.token) {
      applyToken(msg.token, Date.now());
    }
  });

  const origOpen = XMLHttpRequest.prototype.open;
  const origSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;

  XMLHttpRequest.prototype.open = function (method, url) {
    this._zenotiUrl = url;
    this._zenotiHeaders = {};
    return origOpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
    if (TOKEN_HEADER_KEYS.includes(name.toLowerCase())) {
      this._zenotiHeaders[name.toLowerCase()] = value;
      const token = value.replace(/^Bearer\s+/i, "").trim();
      if (token && token !== currentToken) {
        applyToken(token, Date.now());
      }
    }
    return origSetRequestHeader.apply(this, arguments);
  };

  const origFetch = window.fetch;
  window.fetch = function (input, init = {}) {
    const headers = init.headers || {};
    const check = (obj) => {
      for (const key of TOKEN_HEADER_KEYS) {
        const val = obj[key] || obj[key.split("-").map((w, i) => i ? w[0].toUpperCase() + w.slice(1) : w).join("")];
        if (val) {
          const token = val.replace(/^Bearer\s+/i, "").trim();
          if (token && token !== currentToken) applyToken(token, Date.now());
        }
      }
    };
    if (headers && typeof headers === "object") {
      if (headers instanceof Headers) {
        TOKEN_HEADER_KEYS.forEach((k) => {
          const v = headers.get(k);
          if (v) {
            const token = v.replace(/^Bearer\s+/i, "").trim();
            if (token && token !== currentToken) applyToken(token, Date.now());
          }
        });
      } else {
        check(headers);
      }
    }
    return origFetch.apply(this, arguments);
  };

  buildPanel();
  startTicker();
})();
