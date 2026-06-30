package zenoti

import org.scalajs.dom
import org.scalajs.dom.{document, window, Element, HTMLElement, HTMLButtonElement}
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.JSConverters.*

object Content:

  private val TokenHeaderKeys = List("authorization", "x-session-token", "x-auth-token", "x-zenoti-token")

  private var currentToken: Option[String]  = None
  private var tokenCapturedAt: Option[Double] = None
  private var tickHandle: Int = 0

  case class PlaceholderQuery(id: String, label: String, endpoint: String)

  private val Queries = List(
    PlaceholderQuery("q1", "Current User / Profile",    "/v1/users/me"),
    PlaceholderQuery("q2", "Centers / Locations",        "/v1/centers"),
    PlaceholderQuery("q3", "Upcoming Appointments",      "/v1/appointments?status=upcoming&limit=5"),
    PlaceholderQuery("q4", "Memberships",                "/v1/memberships"),
    PlaceholderQuery("q5", "Classes / Schedule",         "/v1/classes?limit=5"),
  )

  def main(args: Array[String]): Unit =
    if document.getElementById("zenoti-inspector-panel") != null then return
    buildPanel()
    startTicker()
    interceptRequests()

    Chrome.ns.runtime.sendMessage(
      js.Dynamic.literal(`type` = "GET_TOKEN"),
      (resp: js.Any) =>
        if resp != null then
          val d = resp.asInstanceOf[js.Dynamic]
          val tok = d.token
          if !js.isUndefined(tok) && tok != null then
            applyToken(tok.asInstanceOf[String], Some(d.tokenCapturedAt.asInstanceOf[Double]))
    )

    Chrome.ns.runtime.onMessage.addListener(
      (msg: js.Dynamic, _: js.Any, _: js.Function1[js.Any, Unit]) =>
        if msg.`type`.asInstanceOf[String] == "TOKEN_UPDATED" then
          applyToken(msg.token.asInstanceOf[String], None)
        false
    )

  // ── Token handling ────────────────────────────────────────────────────────

  private def applyToken(token: String, capturedAt: Option[Double]): Unit =
    if currentToken.forall(_ != token) then
      currentToken     = Some(token)
      tokenCapturedAt  = Some(capturedAt.getOrElse(js.Date.now()))
      renderTokenUI()
      setStatus("Token captured from live request.")

  private def parseJwtExpiry(token: String): Option[Double] =
    try
      val parts = token.split("\\.")
      if parts.length != 3 then return None
      val padded  = parts(1).replace('-', '+').replace('_', '/')
      val decoded = js.Dynamic.global.atob(padded).asInstanceOf[String]
      val payload = js.JSON.parse(decoded)
      val exp = payload.exp
      if js.isUndefined(exp) then None
      else Some(exp.asInstanceOf[Double] * 1000)
    catch case _: Throwable => None

  private def formatTimeRemaining(expiryMs: Double): (String, String) =
    val diff = expiryMs - js.Date.now()
    if diff <= 0 then ("EXPIRED", "expired")
    else
      val s = (diff / 1000).toInt
      val m = s / 60
      val h = m / 60
      if h > 0 then (s"${h}h ${m % 60}m remaining", "ok")
      else if m > 5 then (s"${m}m ${s % 60}s remaining", "ok")
      else if m > 0 then (s"${m}m ${s % 60}s remaining", "warn")
      else (s"${s}s remaining", "danger")

  private def truncate(s: String, n: Int = 60): String =
    if s.length > n then s.take(n) + "…" else s

  private def formatDate(ms: Double): String =
    new js.Date(ms).toLocaleString()

  // ── Panel DOM ─────────────────────────────────────────────────────────────

  private def buildPanel(): Unit =
    val panel = document.createElement("div").asInstanceOf[HTMLElement]
    panel.id = "zenoti-inspector-panel"
    panel.innerHTML = s"""
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
            <div class="zip-meta-row"><span class="zip-label">Captured</span><span id="zip-captured-at">—</span></div>
            <div class="zip-meta-row"><span class="zip-label">Expires</span><span id="zip-expiry-abs">—</span></div>
            <div class="zip-meta-row"><span class="zip-label">Time left</span><span id="zip-expiry-rel" class="zip-badge">—</span></div>
          </div>
          <div id="zip-token-actions">
            <button class="zip-btn" id="zip-copy-btn" disabled>Copy Token</button>
            <button class="zip-btn zip-btn-secondary" id="zip-clear-btn" disabled>Clear</button>
          </div>
        </section>
        <section class="zip-section">
          <div class="zip-section-title">API Queries<span class="zip-hint">Click to run against the live API</span></div>
          <div id="zip-queries">
            ${Queries.map(q => s"""
              <div class="zip-query-item" id="zip-q-${q.id}">
                <div class="zip-query-header">
                  <span class="zip-query-label">${q.label}</span>
                  <span class="zip-query-endpoint zip-mono">${q.endpoint}</span>
                  <button class="zip-run-btn" data-id="${q.id}" data-endpoint="${q.endpoint}" disabled>Run</button>
                </div>
                <div class="zip-query-result zip-mono" id="zip-result-${q.id}">—</div>
              </div>""").mkString}
          </div>
        </section>
        <div id="zip-footer">
          <span id="zip-status">No token captured yet. Browse the site to trigger a request.</span>
        </div>
      </div>"""

    document.body.appendChild(panel)
    makeDraggable(panel, document.getElementById("zip-header").asInstanceOf[HTMLElement])

    document.getElementById("zip-close").addEventListener("click", (_: dom.Event) =>
      panel.remove()
      window.clearInterval(tickHandle)
    )

    document.getElementById("zip-minimize").addEventListener("click", (_: dom.Event) =>
      val body  = document.getElementById("zip-body").asInstanceOf[HTMLElement]
      val btn   = document.getElementById("zip-minimize").asInstanceOf[HTMLElement]
      val isMin = body.style.display == "none"
      body.style.display = if isMin then "" else "none"
      btn.textContent    = if isMin then "−" else "+"
    )

    document.getElementById("zip-copy-btn").addEventListener("click", (_: dom.Event) =>
      currentToken.foreach { t =>
        window.navigator.clipboard.writeText(t).`then`[Unit](_ => setStatus("Token copied to clipboard."))
      }
    )

    document.getElementById("zip-clear-btn").addEventListener("click", (_: dom.Event) =>
      currentToken    = None
      tokenCapturedAt = None
      renderTokenUI()
      setStatus("Token cleared.")
      Chrome.ns.storage.session.remove(js.Array("token", "tokenCapturedAt"))
    )

    document.querySelectorAll(".zip-run-btn").foreach { node =>
      node.addEventListener("click", (_: dom.Event) =>
        val btn = node.asInstanceOf[js.Dynamic]
        runQuery(btn.dataset.id.asInstanceOf[String], btn.dataset.endpoint.asInstanceOf[String])
      )
    }

  private def renderTokenUI(): Unit =
    val box         = document.getElementById("zip-token-value")
    val capturedEl  = document.getElementById("zip-captured-at")
    val expiryAbs   = document.getElementById("zip-expiry-abs")
    val expiryRel   = document.getElementById("zip-expiry-rel").asInstanceOf[HTMLElement]
    val copyBtn     = document.getElementById("zip-copy-btn").asInstanceOf[HTMLButtonElement]
    val clearBtn    = document.getElementById("zip-clear-btn").asInstanceOf[HTMLButtonElement]

    currentToken match
      case None =>
        box.textContent         = "Waiting for a request…"
        capturedEl.textContent  = "—"
        expiryAbs.textContent   = "—"
        expiryRel.textContent   = "—"
        expiryRel.className     = "zip-badge"
        copyBtn.disabled        = true
        clearBtn.disabled       = true
        document.querySelectorAll(".zip-run-btn").foreach(
          _.asInstanceOf[HTMLButtonElement].disabled = true
        )

      case Some(token) =>
        box.textContent        = truncate(token)
        box.asInstanceOf[HTMLElement].title = token
        capturedEl.textContent = tokenCapturedAt.map(formatDate).getOrElse("—")
        copyBtn.disabled       = false
        clearBtn.disabled      = false
        document.querySelectorAll(".zip-run-btn").foreach(
          _.asInstanceOf[HTMLButtonElement].disabled = false
        )
        parseJwtExpiry(token) match
          case Some(expiry) =>
            expiryAbs.textContent = formatDate(expiry)
            updateExpiryBadge(expiry, expiryRel)
          case None =>
            expiryAbs.textContent = "Non-JWT token"
            expiryRel.textContent = "Unknown"
            expiryRel.className   = "zip-badge"

  private def updateExpiryBadge(expiryMs: Double, el: HTMLElement): Unit =
    val (label, cls) = formatTimeRemaining(expiryMs)
    el.textContent = label
    el.className   = s"zip-badge zip-badge-$cls"

  private def startTicker(): Unit =
    window.clearInterval(tickHandle)
    tickHandle = window.setInterval(
      () =>
        currentToken.foreach { token =>
          parseJwtExpiry(token).foreach { expiry =>
            Option(document.getElementById("zip-expiry-rel")).foreach { el =>
              updateExpiryBadge(expiry, el.asInstanceOf[HTMLElement])
            }
          }
        },
      1000
    )

  private def setStatus(msg: String): Unit =
    Option(document.getElementById("zip-status")).foreach(_.textContent = msg)

  private def runQuery(id: String, endpoint: String): Unit =
    currentToken.foreach { token =>
      val resultEl = document.getElementById(s"zip-result-$id").asInstanceOf[HTMLElement]
      val btn      = document.querySelector(s".zip-run-btn[data-id='$id']").asInstanceOf[HTMLButtonElement]
      resultEl.textContent = "Loading…"
      resultEl.className   = "zip-query-result zip-mono zip-loading"
      btn.disabled         = true

      Chrome.ns.runtime.sendMessage(
        js.Dynamic.literal(`type` = "FETCH_API", endpoint = endpoint, token = token),
        (resp: js.Any) =>
          btn.disabled = false
          if resp == null then
            resultEl.textContent = "Error: No response from background."
            resultEl.className   = "zip-query-result zip-mono zip-error"
          else
            val d = resp.asInstanceOf[js.Dynamic]
            if d.ok.asInstanceOf[Boolean] then
              resultEl.textContent = js.JSON.stringify(d.data, null, 2)
              resultEl.className   = "zip-query-result zip-mono zip-success"
            else
              resultEl.textContent = s"Error: ${d.error}"
              resultEl.className   = "zip-query-result zip-mono zip-error"
      )
    }

  private def makeDraggable(panel: HTMLElement, handle: HTMLElement): Unit =
    var startX, startY, startLeft, startTop = 0.0
    handle.style.cursor = "grab"

    handle.addEventListener("mousedown", (e: dom.MouseEvent) =>
      if e.target.asInstanceOf[HTMLElement].tagName != "BUTTON" then
        startX = e.clientX
        startY = e.clientY
        val rect = panel.getBoundingClientRect()
        startLeft = rect.left
        startTop  = rect.top
        panel.style.right  = "auto"
        panel.style.bottom = "auto"
        panel.style.left   = s"${startLeft}px"
        panel.style.top    = s"${startTop}px"
        handle.style.cursor = "grabbing"

        val onMove: js.Function1[dom.MouseEvent, Unit] = (e: dom.MouseEvent) =>
          panel.style.left = s"${math.max(0, startLeft + e.clientX - startX)}px"
          panel.style.top  = s"${math.max(0, startTop  + e.clientY - startY)}px"

        // var breaks the forward-reference cycle (onUp removes itself)
        var onUp: js.Function1[dom.MouseEvent, Unit] = null
        onUp = (_: dom.MouseEvent) =>
          handle.style.cursor = "grab"
          document.removeEventListener("mousemove", onMove)
          document.removeEventListener("mouseup",   onUp)

        document.addEventListener("mousemove", onMove)
        document.addEventListener("mouseup",   onUp)
    )

  // ── Request interception ──────────────────────────────────────────────────

  private def interceptRequests(): Unit =
    val origFetch = js.Dynamic.global.fetch.asInstanceOf[js.Function2[js.Any, js.Any, js.Promise[Response]]]

    js.Dynamic.global.fetch = js.Any.fromFunction2 { (input: js.Any, init: js.Any) =>
      val safeInit: js.Dynamic =
        if init == null || js.isUndefined(init) then js.Dynamic.literal() else init.asInstanceOf[js.Dynamic]
      val headers = safeInit.headers
      if !js.isUndefined(headers) && headers != null then
        TokenHeaderKeys.foreach { key =>
          val v = headers.asInstanceOf[js.Dynamic].selectDynamic(key)
          if !js.isUndefined(v) && v != null then
            val raw = v.asInstanceOf[String].replaceFirst("(?i)^Bearer\\s+", "").trim
            if raw.nonEmpty then applyToken(raw, None)
        }
      origFetch(input, init)
    }
