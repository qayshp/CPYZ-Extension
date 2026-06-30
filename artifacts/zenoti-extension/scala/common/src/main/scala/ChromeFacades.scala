package zenoti

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.|

// ── Minimal Chrome extension API facades ───────────────────────────────────

@js.native @JSGlobalScope
object ChromeGlobal extends js.Object:
  val chrome: ChromeNamespace = js.native

@js.native
trait ChromeNamespace extends js.Object:
  val webRequest: WebRequestNamespace = js.native
  val storage: StorageNamespace       = js.native
  val tabs: TabsNamespace             = js.native
  val runtime: RuntimeNamespace       = js.native

// ── webRequest ─────────────────────────────────────────────────────────────

@js.native
trait WebRequestNamespace extends js.Object:
  val onBeforeSendHeaders: WebRequestEvent = js.native
  val onHeadersReceived: WebRequestEvent   = js.native

@js.native
trait WebRequestEvent extends js.Object:
  def addListener(
    callback: js.Function1[RequestDetails, js.Any],
    filter: js.Object,
    extraInfoSpec: js.Array[String]
  ): Unit = js.native

@js.native
trait RequestDetails extends js.Object:
  val requestHeaders:  js.UndefOr[js.Array[HttpHeader]] = js.native
  val responseHeaders: js.UndefOr[js.Array[HttpHeader]] = js.native
  val tabId: Int = js.native

@js.native
trait HttpHeader extends js.Object:
  val name:  String = js.native
  val value: js.UndefOr[String] = js.native

// ── storage ────────────────────────────────────────────────────────────────

@js.native
trait StorageNamespace extends js.Object:
  val session: StorageArea = js.native

@js.native
trait StorageArea extends js.Object:
  def set(items: js.Object): js.Promise[Unit] = js.native
  def get(keys: js.Array[String]): js.Promise[js.Dictionary[js.Any]] = js.native
  def remove(keys: js.Array[String]): js.Promise[Unit] = js.native

// ── tabs ───────────────────────────────────────────────────────────────────

@js.native
trait TabsNamespace extends js.Object:
  def query(queryInfo: js.Object, callback: js.Function1[js.Array[Tab], Unit]): Unit = js.native
  def sendMessage(tabId: Int, message: js.Object): js.Promise[js.Any] = js.native

@js.native
trait Tab extends js.Object:
  val id:  js.UndefOr[Int]    = js.native
  val url: js.UndefOr[String] = js.native

// ── runtime ────────────────────────────────────────────────────────────────

@js.native
trait RuntimeNamespace extends js.Object:
  val onMessage: MessageEvent = js.native
  def sendMessage(message: js.Object, callback: js.Function1[js.Any, Unit]): Unit = js.native

@js.native
trait MessageEvent extends js.Object:
  def addListener(
    callback: js.Function3[js.Dynamic, js.Any, js.Function1[js.Any, Unit], js.Any]
  ): Unit = js.native

// ── fetch / XHR helpers ────────────────────────────────────────────────────

@js.native @JSGlobal
class XMLHttpRequest extends js.Object:
  def open(method: String, url: String, async: Boolean = true): Unit = js.native
  def setRequestHeader(name: String, value: String): Unit = js.native
  def send(body: js.Any = js.undefined): Unit = js.native
  var onload: js.Function0[Unit] = js.native

@js.native @JSGlobal("fetch")
object NativeFetch extends js.Object:
  def apply(input: String | js.Object, init: js.Object = js.Dynamic.literal()): js.Promise[Response] = js.native

@js.native
trait Response extends js.Object:
  def json(): js.Promise[js.Dynamic] = js.native

// ── Helpers ────────────────────────────────────────────────────────────────

object Chrome:
  val ns: ChromeNamespace = ChromeGlobal.chrome
