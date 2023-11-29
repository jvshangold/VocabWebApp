package driver

import java.net.URI
import java.net.http.HttpRequest
import scala.concurrent.duration.Duration

import cs214.webapp.*
import cs214.webapp.server.WebServer

abstract class WebappTest[Event, State, View] extends munit.FunSuite:
  protected val UID0: String = "yak"
  protected val UID1: String = "hut"
  protected val UID2: String = "kik"

  val sm: StateMachine[Event, State, View]

  override val munitTimeout: Duration = Duration(10, "s")

  protected lazy val wsClient: TestWebsocketClientFactory = TestWebsocketClientFactory(Config.WS_PORT)
  protected lazy val httpClient: TestHttpClient =
    val client = TestHttpClient(Config.HTTP_PORT)
    client.checkConnection()
    client

  /// Testing helpers

  def assertSuccess[V](ts: util.Try[V]): V =
    assert(ts.isSuccess, f"Unexpected failure: $ts")
    ts.get

  def assertSingleRender[V](actions: Seq[messages.Action[V]]): V =
    assertEquals(actions.length, 1)
    val r = actions(0).assertInstanceOf[messages.Action.Render[V]]
    r.st

  def assertSingleRender[V](ts: util.Try[Seq[messages.Action[V]]]): V =
    assertSingleRender(assertSuccess(ts))

  def assertMultipleActions[K](ts: util.Try[Seq[messages.Action[K]]], n: Int): Seq[messages.Action[K]] =
    val actions = assertSuccess(ts)
    assertEquals(actions.length, n)
    actions.map(_.asInstanceOf[messages.Action[K]])

  // [Any] is a trick to avoid using [?]
  inline def assertFailure[F <: Throwable](ts: util.Try[Seq[messages.Action[Any]]]): F =
    assert(ts.isFailure)
    ts.failed.get.assertInstanceOf[F]

  def testWire[T](w: WireFormat[T])(t: T): T =
    val d = w.decode(w.encode(t))
    assert(d.isSuccess)
    assertEquals(t, d.get)
    t

  extension (v: View)
    def testViewWire: View = testWire(sm.wire.viewFormat)(v)

  extension (e: Event)
    def testEventWire: Event = testWire(sm.wire.eventFormat)(e)

  def playSolo(handler: (AppInstanceId, SocketConnection) => Unit): Unit =
    createSockets(Seq(UID0)): (appId, sockets) =>
      handler(appId, sockets(UID0))

  def playWithTwoPeople(handler: (AppInstanceId, SocketConnection, SocketConnection) => Unit): Unit =
    createSockets(Seq(UID0, UID1)): (appId, sockets) =>
      handler(appId, sockets(UID0), sockets(UID1))

  /// Connection helpers

  def initializeApp(userIds: Seq[UserId]): String =
    val url = f"http://localhost:${Config.HTTP_PORT}${Endpoints.Api.initializeApp}"
    val reqBody = messages.InitializeAppRequest(sm.name, userIds)
    val reqBodyStr = messages.InitializeAppRequest.Wire.encode(reqBody).toString
    val req = HttpRequest.newBuilder(URI(url))
      .headers("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(reqBodyStr))
      .build()

    val resp = httpClient.send(req)
    val respBody = resp.body()
    assertEquals(resp.statusCode(), 200, f"Connection error on $url: $respBody")

    messages.InitializeAppResponse.Wire.decode(ujson.read(respBody)).get.appId

  def createSockets(userIds: Seq[UserId])(handler: (AppInstanceId, Map[UserId, SocketConnection]) => Unit): Unit =
    val appId = initializeApp(userIds)
    def loop(userIds: Seq[UserId], sockets: Map[UserId, SocketConnection]): Unit =
      userIds match
        case Seq()    => handler(appId, sockets)
        case hd +: tl => wsClient.useNew(appId, hd)(sock => loop(tl, sockets + (hd -> sock)))
    (appId, loop(userIds, Map.empty))

  extension (socket: SocketConnection)
    def sendEvent(event: Event): Boolean =
      socket.sendEncoded(sm.wire.eventFormat.encode(event))
