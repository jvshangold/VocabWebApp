package cs214.webapp
package messages

import ujson.*
import java.util.UUID
import scala.util.{Failure, Success, Try}

import exceptions.DecodingException
import wires.*

/** An action that client should perform */
enum Action[+StateOrView]:
  /** Show a message box. */
  case Alert(msg: String)

  /** Wait before processing the next action. */
  case Pause(durationMs: Int)

  /** Redraw the UI with new data. */
  case Render(st: StateOrView)

  def map[T](f: StateOrView => T): Action[T] =
    this match
      case Alert(msg)        => Alert(msg)
      case Pause(durationMs) => Pause(durationMs)
      case Render(st)        => Render(f(st))

object Action:
  object Wire extends WireFormat[Action[Value]]:
    def encode(t: Action[Value]): Value =
      t match
        case Alert(msg)        => Obj("type" -> "Alert", "msg" -> msg)
        case Pause(durationMs) => Obj("type" -> "Pause", "durationMs" -> durationMs)
        case Render(js)        => Obj("type" -> "Render", "js" -> js)

    def decode(json: Value): Try[Action[Value]] = Try:
      json("type").str match
        case "Alert"  => Alert(json("msg").str)
        case "Pause"  => Pause(json("durationMs").num.toInt)
        case "Render" => Render(json("js"))
        case _        => throw DecodingException(f"Unexpected action: $json")

/** A response to an event */
type EventResponse = Try[Seq[Action[ujson.Value]]]
object EventResponse:
  val Wire: WireFormat[EventResponse] = TryWire(SeqWire(Action.Wire))

/** A response to a socket message */
type SocketResponse = Try[ujson.Value]
val SocketResponseWire = TryWire(IdentityWire)

/** A response to the list-apps query */
case class ListAppsResponse(apps: Seq[String])

object ListAppsResponse:
  object Wire extends WireFormat[ListAppsResponse]:
    def encode(t: ListAppsResponse): ujson.Value =
      ujson.Obj("apps" -> Arr(t.apps.map(Str(_))*))
    def decode(js: ujson.Value): Try[ListAppsResponse] = Try:
      ListAppsResponse(js("apps").arr.map(_.str).to(Seq))

/** An HTTP request sent to load a new application for the server */
case class InitializeAppRequest(appName: String, userIds: Seq[UserId])

object InitializeAppRequest:
  object Wire extends WireFormat[InitializeAppRequest]:
    val strsWire = SeqWire(StringWire)
    def encode(t: InitializeAppRequest): ujson.Value =
      ujson.Obj("appName" -> Str(t.appName), "userIds" -> strsWire.encode(t.userIds))
    def decode(js: ujson.Value): Try[InitializeAppRequest] = Try:
      InitializeAppRequest(js("appName").str, strsWire.decode(js("userIds")).get)

case class InitializeAppResponse(appId: AppInstanceId)

object InitializeAppResponse:
  object Wire extends WireFormat[InitializeAppResponse]:
    def encode(t: InitializeAppResponse): ujson.Value =
      ujson.Obj("appId" -> Str(t.appId))
    def decode(js: ujson.Value): Try[InitializeAppResponse] = Try:
      InitializeAppResponse(js("appId").str)

/** The response to an AppInfo query */
case class AppInfoResponse(appId: AppInstanceId, userIds: Seq[UserId], wsEndpoint: String, shareUrl: String)

object AppInfoResponse:
  val strsWire = SeqWire(StringWire)
  object Wire extends WireFormat[AppInfoResponse]:
    def encode(t: AppInfoResponse): ujson.Value =
      ujson.Obj(
        "appId" -> t.appId,
        "userIds" -> strsWire.encode(t.userIds),
        "wsEndpoint" -> t.wsEndpoint,
        "shareUrl" -> t.shareUrl
      )
    def decode(js: ujson.Value): Try[AppInfoResponse] = Try:
      AppInfoResponse(js("appId").str, strsWire.decode(js("userIds")).get, js("wsEndpoint").str, js("shareUrl").str)
