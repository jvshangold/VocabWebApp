package cs214.webapp
package client

import scala.collection.mutable
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.annotation.JSExportTopLevel

import org.scalajs.dom

object WebClient:
  private val appLibrary: mutable.Map[String, ClientApp] = mutable.Map()

  val getApp = appLibrary.get

  def register(clientApp: ClientApp): Unit =
    println(s"Registered ${clientApp.name}'s UI")
    appLibrary.put(clientApp.name, clientApp)

  def start(root: dom.Element): Unit =
    println(f"Registered apps: ${appLibrary.keys.toSeq.sorted}.")
    try
      Component.fromPathName(appLibrary.toMap)(dom.document.location.pathname)
        .renderInto(root)
    catch t => crash(t)

  def navigateTo(page: Component) =
    dom.window.location.pathname = page.pathName

  def crash(t: Throwable) =
    dom.document.querySelector("body").prepend:
      import scalatags.JsDom.all.*
      tag("dialog")(id := "fatal", b("Fatal error: "), t.getMessage).render
    dom.document.querySelector("#fatal").asInstanceOf[dom.html.Dialog].showModal()
    throw t

object Requests:
  import scala.concurrent.Future
  import scala.scalajs.js.Promise
  import concurrent.ExecutionContext.Implicits.global

  import org.scalajs.dom.{Fetch, Headers}

  import messages.*

  /** Sends a POST request to initialize a new app.
    *
    * @param userId
    *   The user id used by the user to connect to the app.
    * @param appName
    *   The name of the app to join, which is used to identify which logic
    *   should be used.
    * @param initArgs
    *   Some initial arguments used by some apps to initialize their state.
    * @return
    *   A future containing the ID of the app instance.
    */
  def createApp(appName: String, userIds: Seq[UserId]): Future[InitializeAppResponse] =
    sendPostRequestWith(Endpoints.Api.initializeApp)(InitializeAppRequest.Wire, InitializeAppResponse.Wire):
      InitializeAppRequest(appName, userIds)

  /** Sends a GET request to get the server's IP. */
  def serverIp: Future[String] =
    Fetch.fetch(Endpoints.Api.ip.toString).toFuture
      .flatMap(_.text().toFuture)
      .logErrors

  /** Sends a GET request to enumerate available apps. */
  def listApps: Future[ListAppsResponse] =
    Fetch.fetch(Endpoints.Api.listApps.toString)
      .decodeResponse(ListAppsResponse.Wire)
      .logErrors

  /** Sends a GET request to retrieve information on an app. */
  def appInfo(appId: AppInstanceId): Future[AppInfoResponse] =
    val api = Endpoints.Api.appInfo
    Fetch.fetch(f"${api.root}/$appId/${api.path}")
      .decodeResponse(AppInfoResponse.Wire)
      .logErrors

  /** Sends a POST request to the specified endpoint.
    *
    * @param endpoint
    *   The endpoint the request is sent to.
    * @param jsonPayload
    *   The json payload which used as the request body.
    * @return
    *   The future response converted to JSON.
    */
  private def sendPostRequestWith[R, S](endpoint: Endpoints.Api)(
      reqWire: WireFormat[R],
      respWire: WireFormat[S]
  )(payload: R) =
    val requestHeaders = dom.Headers()
    requestHeaders.append("Content-Type", "application/json")

    val requestOptions = new dom.RequestInit:
      method = dom.HttpMethod.POST
      headers = requestHeaders
      body = reqWire.encode(payload).toString

    Fetch.fetch(endpoint.toString, requestOptions)
      .decodeResponse(respWire)
      .logErrors

  extension [T](p: Future[T])
    private def logErrors =
      p.andThen { case Failure(t) => WebClient.crash(t) }

  case class FailedRequestException(status: Int, msg: String) extends Exception(msg)

  extension (p: Promise[dom.Response])
    private def decodeResponse[R](wire: WireFormat[R]) =
      p.toFuture.flatMap: response =>
        response.text().toFuture.flatMap: txt =>
          if !response.ok then
            throw FailedRequestException(response.status, txt)
          Future.fromTry(wire.decode(ujson.read(txt)))
