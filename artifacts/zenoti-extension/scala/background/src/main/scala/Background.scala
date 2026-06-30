package zenoti

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.JSConverters.*

object Background:

  private val TokenHeaderKeys = Set("authorization", "x-session-token", "x-auth-token", "x-zenoti-token")
  private val ZenotiUrls      = js.Array("https://corepoweryoga.zenoti.com/*", "https://*.zenoti.com/*")

  private var capturedToken: js.UndefOr[String]  = js.undefined
  private var tokenCapturedAt: js.UndefOr[Double] = js.undefined

  def main(args: Array[String]): Unit =
    registerWebRequestListeners()
    registerMessageListener()

  // ── webRequest listeners ──────────────────────────────────────────────────

  private def registerWebRequestListeners(): Unit =
    val filter = js.Dynamic.literal(urls = ZenotiUrls)

    Chrome.ns.webRequest.onBeforeSendHeaders.addListener(
      (details: RequestDetails) =>
        extractTokenFromHeaders(details.requestHeaders).foreach(maybeUpdate),
      filter,
      js.Array("requestHeaders")
    )

    Chrome.ns.webRequest.onHeadersReceived.addListener(
      (details: RequestDetails) =>
        extractTokenFromResponseHeaders(details.responseHeaders).foreach(maybeUpdate),
      filter,
      js.Array("responseHeaders")
    )

  private def extractTokenFromHeaders(
    headers: js.UndefOr[js.Array[HttpHeader]]
  ): Option[String] =
    headers.toOption.flatMap { hdrs =>
      hdrs.toSeq.collectFirst {
        case h if TokenHeaderKeys.contains(h.name.toLowerCase) && h.value.isDefined =>
          h.value.get.replaceFirst("(?i)^Bearer\\s+", "").trim
      }.filter(_.nonEmpty)
    }

  private def extractTokenFromResponseHeaders(
    headers: js.UndefOr[js.Array[HttpHeader]]
  ): Option[String] =
    headers.toOption.flatMap { hdrs =>
      hdrs.toSeq.collectFirst {
        case h if Set("x-session-token", "set-zenoti-token").contains(h.name.toLowerCase) && h.value.isDefined =>
          h.value.get.trim
      }.filter(_.nonEmpty)
    }

  private def maybeUpdate(token: String): Unit =
    if capturedToken.forall(_ != token) then
      capturedToken   = token
      tokenCapturedAt = js.Date.now()
      broadcastToken(token)
      Chrome.ns.storage.session.set(
        js.Dynamic.literal(token = token, tokenCapturedAt = js.Date.now())
      )

  private def broadcastToken(token: String): Unit =
    Chrome.ns.tabs.query(
      js.Dynamic.literal(url = ZenotiUrls),
      (tabs: js.Array[Tab]) =>
        tabs.foreach { tab =>
          tab.id.foreach { id =>
            val p = Chrome.ns.tabs.sendMessage(
              id,
              js.Dynamic.literal(`type` = "TOKEN_UPDATED", token = token)
            )
            // cast to Dynamic to avoid scala.Any vs js.Any mismatch in Promise.catch
            p.asInstanceOf[js.Dynamic].`catch`(js.Any.fromFunction1((_: js.Any) => ()))
          }
        }
    )

  // ── message listener ──────────────────────────────────────────────────────

  private def registerMessageListener(): Unit =
    Chrome.ns.runtime.onMessage.addListener(
      (msg: js.Dynamic, _sender: js.Any, sendResponse: js.Function1[js.Any, Unit]) =>
        val msgType = msg.`type`.asInstanceOf[String]
        msgType match

          case "GET_TOKEN" =>
            Chrome.ns.storage.session
              .get(js.Array("token", "tokenCapturedAt"))
              .`then`[Unit] { result =>
                val d = result.asInstanceOf[js.Dictionary[js.Any]]
                sendResponse(
                  js.Dynamic.literal(
                    token           = d.get("token").orUndefined,
                    tokenCapturedAt = d.get("tokenCapturedAt").orUndefined,
                  )
                )
              }
            true

          case "FETCH_API" =>
            val endpoint = msg.endpoint.asInstanceOf[String]
            val token    = msg.token.asInstanceOf[String]
            val promise  = js.Dynamic.global.fetch(
              s"https://corepoweryoga.zenoti.com$endpoint",
              js.Dynamic.literal(
                headers = js.Dynamic.literal(
                  Authorization  = s"Bearer $token",
                  `Content-Type` = "application/json",
                  Accept         = "application/json",
                ),
                credentials = "include",
              )
            )
            promise
              .`then`(js.Any.fromFunction1((r: js.Dynamic) => r.json()))
              .`then`(js.Any.fromFunction1((data: js.Dynamic) =>
                sendResponse(js.Dynamic.literal(ok = true, data = data))
              ))
              .`catch`(js.Any.fromFunction1((err: js.Any) =>
                sendResponse(js.Dynamic.literal(ok = false, error = err.toString))
              ))
            true

          case _ => false
    )
