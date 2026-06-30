# Zenoti Session Inspector

A Chrome extension that injects a panel into `corepoweryoga.zenoti.com` to inspect your session token and run API queries.

## Features

- **Auto-captures** the session token from outgoing requests (Authorization header, XHR, and fetch interception)
- **Token expiry display** — shows absolute expiry time and a live countdown (green/yellow/red/expired)
- **API query runner** — 5 pre-configured placeholder endpoints you can run with one click using your live token
- **Draggable panel** — drag by the header, minimize or close at any time
- **Copy token** — one click to clipboard

## Installation

1. Open Chrome and go to `chrome://extensions`
2. Enable **Developer mode** (top-right toggle)
3. Click **Load unpacked**
4. Select this folder (`zenoti-extension/`)
5. Navigate to `https://corepoweryoga.zenoti.com` — the panel appears automatically

## How it captures the token

The extension watches for the `Authorization: Bearer <token>` header on any outgoing request to `*.zenoti.com`. It also checks `x-session-token`, `x-auth-token`, and `x-zenoti-token` headers. Once captured, the token is stored in `chrome.storage.session` and persists until you close the browser or clear it manually.

## Adding your own API endpoints

Edit `content.js` and update the `PLACEHOLDER_QUERIES` array near the top:

```js
const PLACEHOLDER_QUERIES = [
  { id: "q1", label: "My Custom Query", endpoint: "/v1/your/endpoint" },
  // ...
];
```

Each query fires a GET request to `https://corepoweryoga.zenoti.com{endpoint}` with your Bearer token attached.

## Notes

- The panel only injects on `corepoweryoga.zenoti.com` and `*.zenoti.com` pages
- Token expiry is decoded from the JWT payload (`exp` field). Non-JWT tokens show "Unknown"
- The extension does not send your token anywhere — all requests go directly to Zenoti
