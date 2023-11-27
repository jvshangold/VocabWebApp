package driver

import java.net.URI
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

import cs214.webapp.*
import cs214.webapp.messages.*

type MessageConsumer = String => Unit
type OnComplete = () => Unit
type MessageProducer = String => Unit

type TestResponse = Try[Action[ujson.Value]]

class SocketConnection(endPoint: URI):
  var debug = false

  private object Locker
  private var received: Boolean = false
  private val responseBuffer: mutable.Queue[TestResponse] = mutable.Queue()

  private def onServerMessage(msg: String): Unit =
    Locker.synchronized {
      val js = ujson.read(msg)
      SocketResponseWire.decode(js).flatten.flatMap(EventResponse.Wire.decode) match
        case Failure(exn) =>
          throw exn // Bad JSON or server crashed
        case Success(Failure(t)) =>
          if debug then println(s"[Test#Debug]: Received failure: $t")
          responseBuffer.enqueue(Failure(t))
        case Success(Success(actions)) =>
          if debug then println(s"[Test#Debug]: Received actions: $actions")
          responseBuffer.enqueueAll(actions.map(a => Success(a)))
      received = true
      Locker.notifyAll()
    }

  lazy val socket: WebSocketClient = new WebSocketClient(endPoint):
    setReuseAddr(true) // Ignore leftover connections from dead processes
    override def onOpen(handshakeData: ServerHandshake): Unit = {}
    override def onClose(code: Int, reason: String, remote: Boolean): Unit = {}
    override def onError(ex: Exception): Unit = throw ex
    override def onMessage(message: String): Unit = onServerMessage(message)

  private def readReceivedResponse(): TestResponse =
    assert(responseBuffer.nonEmpty, "A message should have been received")
    responseBuffer.dequeue()

  def readException(): Throwable =
    val response = readReceivedResponse()
    assert(response.isFailure, f"Expecting an exception, got $response")
    response.failed.get

  def readSuccessfullyDecodedAction[View](wv: WireFormat[View]): Action[View] =
    val action = Try(readReceivedResponse().get.map(js => wv.decode(js).get))
    assert(action.isSuccess, f"Expecting a success, got $action")
    action.get

  def readSuccessfullyDecodedView[View](wv: WireFormat[View]): View =
    val rd = readSuccessfullyDecodedAction(wv)
    rd.assertInstanceOf[Action.Render[View]].st

  def send(msg: String): Boolean =
    Locker.synchronized {
      socket.send(msg)
      waitForMessage()
    }
    // If the type is an error, other users won't get a message
    responseBuffer.last.isSuccess

  def sendEncoded(js: ujson.Value): Boolean =
    send(js.toString)

  def dropReceivedMessages(): Unit =
    responseBuffer.clear()

  def dropReceivedMessagesExceptLast(): Unit =
    responseBuffer.dropInPlace(responseBuffer.size - 1)

  def waitForMessage(): Unit =
    Locker.synchronized {
      while !received do
        // Wait for the response message to be received
        Locker.wait() // Should not be infinite here
      // Reset the msg received flag
      received = false
    }

class TestWebsocketClientFactory(port: Int):
  /** Blocks until the socket can establish a connection to the websocket server
    * and then until the caller is finished with the socket.
    *
    * @note
    *   Might block indefinitely if the server is not reachable. It then
    *   provides the caller with a [[SocketConnection]] that they can use to
    *   send and receive message to/from the server.
    * @param appId
    *   The [[AppInstanceId]] of the app instance to connect to
    * @param userId
    *   The [[UserId]] to use to connect to an app instance
    * @param socketHandler
    *   A method to access the connected socket
    */
  def useNew(appId: AppInstanceId, userId: UserId)(socketHandler: SocketConnection => Unit): Unit =
    val url = URI(s"ws://localhost:$port/$appId/$userId")

    // Close previous connection if any
    val connection = SocketConnection(url)
    while !connection.socket.connectBlocking() do {}

    // When the socket connection is first opened, wait for the first message
    connection.waitForMessage()

    // Provide the connected socket, this call is blocking until the user gets out of the scope
    socketHandler(connection)

    connection.socket.closeBlocking()
