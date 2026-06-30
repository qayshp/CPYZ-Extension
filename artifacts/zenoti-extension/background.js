const ZENOTI_ORIGINS = ["https://corepoweryoga.zenoti.com", "https://zenoti.com"];

let capturedToken = null;
let tokenCapturedAt = null;

chrome.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    const headers = details.requestHeaders || [];
    for (const header of headers) {
      const name = header.name.toLowerCase();
      if (name === "authorization" && header.value) {
        const token = header.value.replace(/^Bearer\s+/i, "").trim();
        if (token && token !== capturedToken) {
          capturedToken = token;
          tokenCapturedAt = Date.now();
          broadcastToken(token);
          chrome.storage.session.set({ token, tokenCapturedAt });
        }
        break;
      }
    }
    for (const header of headers) {
      const name = header.name.toLowerCase();
      if ((name === "x-session-token" || name === "x-auth-token" || name === "x-zenoti-token") && header.value) {
        const token = header.value.trim();
        if (token && token !== capturedToken) {
          capturedToken = token;
          tokenCapturedAt = Date.now();
          broadcastToken(token);
          chrome.storage.session.set({ token, tokenCapturedAt });
        }
        break;
      }
    }
  },
  { urls: ["https://corepoweryoga.zenoti.com/*", "https://*.zenoti.com/*"] },
  ["requestHeaders"]
);

chrome.webRequest.onHeadersReceived.addListener(
  (details) => {
    const headers = details.responseHeaders || [];
    for (const header of headers) {
      const name = header.name.toLowerCase();
      if (name === "x-session-token" || name === "set-zenoti-token") {
        const token = header.value.trim();
        if (token && token !== capturedToken) {
          capturedToken = token;
          tokenCapturedAt = Date.now();
          broadcastToken(token);
          chrome.storage.session.set({ token, tokenCapturedAt });
        }
        break;
      }
    }
  },
  { urls: ["https://corepoweryoga.zenoti.com/*", "https://*.zenoti.com/*"] },
  ["responseHeaders"]
);

function broadcastToken(token) {
  chrome.tabs.query({ url: ["https://corepoweryoga.zenoti.com/*", "https://*.zenoti.com/*"] }, (tabs) => {
    for (const tab of tabs) {
      chrome.tabs.sendMessage(tab.id, { type: "TOKEN_UPDATED", token }).catch(() => {});
    }
  });
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === "GET_TOKEN") {
    chrome.storage.session.get(["token", "tokenCapturedAt"], (result) => {
      sendResponse({
        token: result.token || null,
        tokenCapturedAt: result.tokenCapturedAt || null,
      });
    });
    return true;
  }

  if (msg.type === "FETCH_API") {
    const { endpoint, token } = msg;
    fetch(`https://corepoweryoga.zenoti.com${endpoint}`, {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      credentials: "include",
    })
      .then((r) => r.json())
      .then((data) => sendResponse({ ok: true, data }))
      .catch((err) => sendResponse({ ok: false, error: err.message }));
    return true;
  }
});
