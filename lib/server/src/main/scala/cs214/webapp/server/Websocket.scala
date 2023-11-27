package cs214.webapp
package server

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

import java.net.{InetSocketAddress, URLDecoder}
import scala.collection.mutable

/** A collection of websockets organized by app Id and client */
abstract class WebsocketsCollection(val port: Int):
  def onMessageReceived(appId: AppInstanceId, clientId: UserId, msg: ujson.Value): Unit
  def onClientConnect(appId: AppInstanceId, clientId: UserId): Unit

  /** All the sessions currently in use, mapping an (UserId, AppId) pair to an
    * actual socket object that a client owns
    */
  private val sessions: mutable.Map[AppInstanceId, Seq[(UserId, WebSocket)]] =
    mutable.Map()

  def initializeApp(appId: AppInstanceId) =
    require(!sessions.contains(appId))
    sessions(appId) = Seq.empty

  /** Runs k with parameters parsed from the websocket connection path. [[k]] is
    * run while synchronizing on [[sessions]]
    *
    * The connection should be on "ws://…/[app_instance_id]/[user_id]" for the
    * parsing to function properly.
    */
  private def withSessionParams[T](socket: WebSocket)(k: (AppInstanceId, UserId) => T) =
    sessions.synchronized:
      try
        val components = socket.getResourceDescriptor.split("/").takeRight(2)
        val decoded = components.map(s => URLDecoder.decode(s, "UTF-8"))
        decoded match
          case Array(appId, userId) =>
            if !sessions.contains(appId) then
              throw IllegalArgumentException("Error: Invalid app ID")
            k(appId, userId)
          case _ => throw Exception("Error: Invalid path")
      catch t =>
          socket.send(messages.SocketResponseWire.encode(util.Failure(t)).toString)
          socket.close()

  /** A single websocket server handling multiple apps and clients. */
  private val server: WebSocketServer = new WebSocketServer(InetSocketAddress("0.0.0.0", port)):
    override def onOpen(socket: WebSocket, handshake: ClientHandshake): Unit =
      withSessionParams(socket): (appId, clientId) =>
        sessions(appId) = sessions(appId) :+ (clientId, socket)
        onClientConnect(appId, clientId)

    override def onClose(socket: WebSocket, code: Int, reason: String, remote: Boolean): Unit =
      withSessionParams(socket): (appId, clientId) =>
        sessions(appId) = sessions(appId).filter(_._2 != socket) // Unregister the session

    override def onMessage(socket: WebSocket, message: String): Unit =
      withSessionParams(socket): (appId, clientId) =>
        onMessageReceived(appId, clientId, ujson.read(message))

    override def onError(socket: WebSocket, ex: Exception): Unit =
      // Only report the error, onClosed is called even when an error occurs
      throw new RuntimeException(ex)

    override def onStart(): Unit =
      val addr = server.getAddress
      println(s"Websocket server started on ${addr.getHostName}:${addr.getPort}.")

  /** Starts the server asynchronously. */
  def run(): Unit =
    server.setReuseAddr(true) // Ignore leftover connections from pending processes
    Thread(() => server.run(), "Socket Thread").start()

  /** Enumerates clients connected to [[appId]]. */
  def connectedClients(appId: AppInstanceId): Seq[UserId] =
    sessions.get(appId).map(_.map(_._1).distinct).getOrElse(Seq())

  /** Sends a message to a specific client. */
  def send(appId: AppInstanceId, clientId: UserId)(message: ujson.Value) =
    val wrapped = messages.SocketResponseWire.encode(util.Success(message)).toString
    sessions.synchronized:
      for
        (userId, socket) <- sessions.getOrElse(appId, Seq.empty)
        if userId == clientId
      do socket.send(wrapped)
