# Zenoti Session Inspector

A Chrome extension that injects a panel into CorePower Yoga's Zenoti portal (`corepoweryoga.zenoti.com`). It automatically captures your session token from live network traffic, displays its expiration countdown, and lets you fire API queries directly against the Zenoti backend using your existing session.

The extension logic is written in **Scala 3** and compiled to JavaScript via **Scala.js**.

---

## Installation

1. Download and unzip `zenoti-extension.zip`
2. Open Chrome and navigate to `chrome://extensions`
3. Enable **Developer mode** using the toggle in the top-right corner
4. Click **Load unpacked** and select the `zenoti-extension` folder
5. Navigate to `https://corepoweryoga.zenoti.com` — the inspector panel appears automatically in the bottom-right corner

> The extension only activates on `corepoweryoga.zenoti.com` and `*.zenoti.com` pages. It does nothing on any other site.

---

## Using the Panel

### Session Token

The panel watches all outgoing network requests for an `Authorization: Bearer <token>` header (as well as `x-session-token`, `x-auth-token`, and `x-zenoti-token`). As soon as your browser makes an authenticated request to the Zenoti domain, the token is captured automatically — no manual steps required.

Once a token is captured, the panel shows:

| Field | Description |
|---|---|
| **Token** | The captured token, truncated. Hover for the full value. |
| **Captured** | Local date and time when the token was first seen. |
| **Expires** | Absolute expiry date decoded from the JWT payload (`exp` claim). |
| **Time left** | Live countdown — green when healthy, yellow when under 5 minutes, red when under 1 minute, expired when past. |

**Copy Token** — copies the full token string to your clipboard.  
**Clear** — removes the token from the panel and from session storage.

### API Queries

Five pre-configured endpoint buttons are included as starting points. Click **Run** next to any endpoint to fire a GET request using your captured token. The JSON response appears inline below the button.

| Label | Endpoint |
|---|---|
| Current User / Profile | `/v1/users/me` |
| Centers / Locations | `/v1/centers` |
| Upcoming Appointments | `/v1/appointments?status=upcoming&limit=5` |
| Memberships | `/v1/memberships` |
| Classes / Schedule | `/v1/classes?limit=5` |

All requests go directly from your browser to `corepoweryoga.zenoti.com` — the extension never sends your token to any third party.

### Panel Controls

- **Drag** — click and drag the header bar to reposition the panel anywhere on the screen.
- **Minimize (−)** — collapses the panel body; click again to expand.
- **Close (✕)** — removes the panel for the current page load. Reload the page to bring it back.

---

## Adding or Editing API Endpoints

Open `content/src/main/scala/Content.scala` and find the `Queries` list near the top of the `Content` object:

```scala
private val Queries = List(
  PlaceholderQuery("q1", "Current User / Profile",    "/v1/users/me"),
  PlaceholderQuery("q2", "Centers / Locations",        "/v1/centers"),
  // add more entries here …
)
```

Each entry takes three fields: a unique `id`, a display `label`, and the API `endpoint` path. After editing, rebuild the extension (see below) and reload it in Chrome.

---

## Building from Scala.js Source

### Prerequisites

- **Java** — JDK 11 or later (GraalVM 22.3+ recommended)
- **sbt** — 1.10 or later ([install guide](https://www.scala-sbt.org/download.html))

Both are already installed if you are working inside the Replit environment.

### Build

```bash
cd artifacts/zenoti-extension/scala
sbt buildExtension
```

This runs `fastLinkJS` on both the `background` and `content` subprojects and copies the compiled JavaScript into `../background.js` and `../content.js` in the extension directory.

### Project Structure

```
zenoti-extension/
├── manifest.json          Chrome extension manifest (MV3)
├── background.js          Compiled from background/src/main/scala/Background.scala
├── content.js             Compiled from content/src/main/scala/Content.scala
├── panel.css              Injected stylesheet for the panel UI
├── icons/                 Extension icons (16 × 16, 48 × 48, 128 × 128)
└── scala/
    ├── build.sbt          Multi-project build + buildExtension task
    ├── project/
    │   ├── build.properties   sbt version pin
    │   └── plugins.sbt        sbt-scalajs plugin
    ├── common/src/main/scala/
    │   └── ChromeFacades.scala   @js.native facades for Chrome extension APIs
    ├── background/src/main/scala/
    │   └── Background.scala      Service worker: token capture, message routing
    └── content/src/main/scala/
        └── Content.scala         Content script: panel UI, ticker, fetch interception
```

### Full Optimization (for distribution)

To produce a smaller, fully optimized build suitable for packaging:

```bash
sbt "background/fullLinkJS" "content/fullLinkJS"
```

Then copy the output files from each subproject's `target/scala-3.4.2/<name>-opt/` directory into the extension root, replacing `background.js` and `content.js`.

---

## How Token Capture Works

The extension uses three complementary strategies:

1. **`chrome.webRequest`** (background service worker) — intercepts all outgoing request headers before they are sent. This catches tokens in XHR and fetch requests made by the page's own JavaScript.

2. **`XMLHttpRequest` override** (content script) — wraps `setRequestHeader` so tokens set directly on XHR objects are captured even if `webRequest` fires before the content script is ready.

3. **`window.fetch` override** (content script) — wraps the global `fetch` function to inspect request headers passed inline with each call.

Captured tokens are stored in `chrome.storage.session` (cleared when the browser closes) and broadcast to all matching tabs via `chrome.tabs.sendMessage`.

---

## Permissions

| Permission | Why it is needed |
|---|---|
| `webRequest` | Read outgoing request headers to extract the session token |
| `storage` | Persist the token across page navigations within the same browser session |
| `cookies` | Reserved for future use (reading session cookies as a fallback) |
| Host: `*.zenoti.com` | Scope all of the above to Zenoti domains only |

---

## Troubleshooting

**Panel does not appear**  
Confirm the extension is loaded and enabled at `chrome://extensions`. Hard-refresh the page (`Ctrl+Shift+R` / `Cmd+Shift+R`).

**Token never captured**  
The token is picked up the first time the site makes an authenticated API call. Try navigating to a different section of the portal (e.g., the booking page) to trigger a request.

**"Non-JWT token" in the expiry field**  
If Zenoti switches to an opaque (non-JWT) token format, the expiry cannot be decoded client-side. The token is still captured and usable for API queries; expiry simply shows as unknown.

**API query returns an error**  
Endpoint paths may change with Zenoti platform updates. Open DevTools → Network, look for XHR requests to `corepoweryoga.zenoti.com`, and update the endpoint list in `Content.scala` accordingly.
