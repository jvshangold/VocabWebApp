package cs214.webapp
package server

import java.net.InetAddress

import cats.*
import cats.Applicative
import cats.effect.IOApp
import cats.effect.*
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, ErrorAction, ErrorHandling}
import org.http4s.server.staticcontent.*

import scala.collection.immutable.{Map}
import scala.collection.{concurrent, immutable, mutable}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters.*

import wires.*
import messages.*
import exceptions.AppException

object WebServer:
  import scala.collection.immutable.Map

  private val appDirectory: mutable.Map[String, ServerApp] = mutable.Map()
  private val apps: concurrent.Map[AppInstanceId, ServerAppInstance] = concurrent.TrieMap()

  /** Registers an arbitrary app. */
  def register(app: ServerApp): Unit =
    println(f"Registered ${app.name}")
    this.appDirectory.put(app.name, app)

  /** Registers an app by calling [[package]].register() */
  def register(packageName: String): Unit =
    Class.forName(f"$packageName.register").getDeclaredConstructor().newInstance()

  /** Registers a state-machine based app. */
  def register[E, V, S](sm: StateMachine[E, V, S]): Unit =
    register(StateMachineServerApp(sm))

  /** Paths where the static content served by the server is stored */
  private val WEB_SRC_PATH = "www/static/"

  /** Path to the html page to serve when accessing the server "/" path */
  private val HTML_APP_PATH = WEB_SRC_PATH + "webapp.html"

  val debug = false

  private def initializeApp(name: String, clients: Seq[UserId]): AppInstanceId =
    val appId = java.util.UUID.randomUUID.toString
    println(f"Creating instance of $name with appId = $appId")
    apps(appId) = appDirectory(name).init(clients)
    websocketServer.initializeApp(appId)
    appId

  /** Creates a transition from the current state of the application to the next
    * state of the application.
    *
    * Given a [[Seq]] of [[UserId]], corresponding to the clients connected to
    * the app, accepts and event and the event's author's id. The [[apps]] is
    * then used to handle the event and gives a sequence of actions for each
    * user.
    *
    * @param clients
    *   The set of clients currently connected in the application.
    * @param uid
    *   The user id of the user who triggered the event.
    * @param event
    *   The event triggered by the user.
    * @return
    *   A map which links each user to a queue of views they should receive.
    */
  private def transition(
      clients: Seq[UserId],
      appId: AppInstanceId
  )(uid: UserId, event: ujson.Value): Try[immutable.Map[UserId, Seq[Action[ujson.Value]]]] =
    apps(appId).synchronized {
      apps(appId).transition(clients)(uid, event).map {
        case (views, newApp) =>
          apps(appId) = newApp
          views
      }
    }

  /** The websocket server */
  private lazy val websocketServer: WebsocketsCollection = new WebsocketsCollection(Config.WS_PORT):
    override def onMessageReceived(appId: AppInstanceId, uid: UserId, msg: ujson.Value): Unit =
      // On an event received by the client, use the transition function on the app instance
      // the client is connected to.
      val transitionResult = transition(websocketServer.connectedClients(appId), appId)(uid, msg)
      val serverResponse = transitionResult match
        case Failure(exception) =>
          websocketServer.send(appId, uid):
            EventResponse.Wire.encode(Failure(exception))
        case Success(userActions) =>
          for (uid, actions) <- userActions do
            websocketServer.send(appId, uid):
              EventResponse.Wire.encode(Success(actions))

    // When the user joins the app, the projection of the current state is sent to them
    override def onClientConnect(appId: AppInstanceId, uid: UserId): Unit =
      val js = apps(appId).respondToNewClient(uid)
      println(f"onClientConnect($appId, $uid)")
      websocketServer.send(appId, uid):
        EventResponse.Wire.encode(Success(List(Action.Render(js))))

  private object HttpServer extends IOApp:
    private val dsl = Http4sDsl[IO]

    /** Reads a static file given its path on the file system, from the resource
      * directory in the classpath
      * @param filePath
      *   The path of the file to read, contained in the classpath's /resources
      *   directory.
      * @return
      *   The content of the file.
      */
    private def readStatic(filePath: String): String =
      val source = scala.io.Source.fromResource(filePath)
      try source.mkString
      finally source.close()

    def guessContentType(path: String) =
      val ext = path.split("[.]").last
      `Content-Type`(MediaType.forExtension(ext).getOrElse(MediaType.text.plain))

    def staticRoutes(systemPrefix: String) = HttpRoutes.of[IO] {
      case req @ GET -> Root / path =>
        val systemPath = systemPrefix + path
        Ok(readStatic(systemPath), guessContentType(path))
    }

    extension (req: Request[IO])
      def decodeWithWire[T](wire: WireFormat[T]): IO[T] =
        req.as[String].flatMap: body =>
          IO.fromTry(wire.decode(ujson.read(body)))

    lazy val hostAddress: String =
      val addresses =
        for
          intf <- java.net.NetworkInterface.getNetworkInterfaces.asScala
          if intf.isUp
          _ = println(f"Found interface $intf")
          addr <- intf.getInetAddresses.asScala
          if (addr.isInstanceOf[java.net.Inet4Address]
            && !addr.isLinkLocalAddress
            && !addr.isLoopbackAddress)
          _ = println(f"Found address ${addr.getHostAddress}")
        yield addr.getHostAddress
      Try(addresses.toList.head).getOrElse(InetAddress.getLocalHost.getHostAddress)

    private val httpApp: HttpApp[IO] =
      val api = HttpRoutes.of[IO] {
        // Gives the ip address of the machine on which this server is running
        case GET -> Root / Endpoints.Api.ip.path =>
          Ok(hostAddress)
        // Lists the different apps available for the server
        case GET -> Root / Endpoints.Api.listApps.path =>
          Ok(ListAppsResponse.Wire.encode(ListAppsResponse(appDirectory.keys.toSeq)).toString)
        // Lists users registered in a given app
        case req @ GET -> Root / appId / Endpoints.Api.appInfo.path =>
          if apps.contains(appId) then
            val app = apps(appId)
            val shareUrl = f"http://$hostAddress:${Config.HTTP_PORT}/app/${app.name}/$appId/"
            val wsEndpoint = f"ws://{{hostName}}:${Config.WS_PORT}/$appId/{{userId}}"
            val response = AppInfoResponse(appId, app.registeredUsers, wsEndpoint, shareUrl)
            Ok(AppInfoResponse.Wire.encode(response).toString)
          else
            BadRequest(f"Unrecognized id $appId")
        // Returns a generated appId the player can use to connect
        case req @ POST -> Root / Endpoints.Api.initializeApp.path =>
          req.decodeWithWire(InitializeAppRequest.Wire).flatMap(body =>
            val appId = initializeApp(body.appName, body.userIds)
            Ok(InitializeAppResponse.Wire.encode(InitializeAppResponse(appId)).toString)
          )
      }

      val pages = HttpRoutes.of[IO] {
        case GET -> _ => // All routing happens client-side
          Ok(readStatic(HTML_APP_PATH), `Content-Type`(MediaType.text.html))
      }

      Router(
        "/api" -> api,
        "/static" -> staticRoutes(WEB_SRC_PATH),
        "/" -> pages
      ).orNotFound

    def printBacktrace(exn: Throwable): IO[Unit] =
      IO.println(exn) >> {
        val sw = java.io.StringWriter()
        exn.printStackTrace(java.io.PrintWriter(sw));
        IO.println(sw.toString)
      }

    private val withErrorLogging = ErrorHandling.Recover.total(
      ErrorAction.log(
        httpApp,
        messageFailureLogAction = (t, _) => printBacktrace(t) >> IO.raiseError(t),
        serviceErrorLogAction = (t, _) => printBacktrace(t) >> IO.raiseError(t)
      )
    )

    private val withCors = CORS.policy
      .withAllowOriginAll(withErrorLogging)

    override def run(args: List[String]): IO[ExitCode] =
      val ipAddress = ipv4"0.0.0.0"
      val port = Port.fromInt(Config.HTTP_PORT).get
      val server = EmberServerBuilder
        .default[IO]
        .withHost(ipAddress)
        .withPort(port)
        .withHttpApp(withCors)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)
      println(f"HTTP Server started on $ipAddress:$port.")
      server

  def start(): Unit =
    websocketServer.run()
    HttpServer.run(List.empty).unsafeRunSync()
