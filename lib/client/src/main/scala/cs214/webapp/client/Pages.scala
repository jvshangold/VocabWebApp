package cs214.webapp
package client

import java.net.{URLEncoder, URLDecoder}

import scala.util.{Failure, Success}
import concurrent.ExecutionContext.Implicits.global

import ujson.*

import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.html.{Input, Select, TextArea}
import scalatags.JsDom.all.*

abstract class Component:
  val classList: String
  def renderInto(target: dom.Element): Unit

  def pageHeader(subtitle: String) =
    frag(h1(cls := "title", "ScalApp"), h2(subtitle))

  def replaceChildren(target: dom.Element)(frag: Frag): Unit =
    target.replaceChildren(frag.render)
    target.setAttribute("class", classList)

  def path: Seq[String] =
    this match
      case HomePage =>
        Nil
      case JoinPageLoader(appName, appId) =>
        List("app", appName, appId)
      case AppPage(appName, appId, userId) =>
        List("app", appName, appId, userId)
      case _ =>
        throw IllegalArgumentException(f"No path for $this!")

  def pathName =
    f"/${path.map(s => URLEncoder.encode(s, "UTF-8")).mkString("/")}"

object Component:
  def fromPathName(appDirectory: Map[String, ClientApp])(pathName: String): Component =
    val components = pathName.stripPrefix("/").stripSuffix("/").split("/")
    val decoded = components.map(c => URLDecoder.decode(c, "UTF-8")).toList
    decoded match
      case List("") =>
        HomePage
      case List("app", appName, appId) =>
        JoinPageLoader(appName, appId)
      case List("app", appName, appId, userId) =>
        AppPage(appName, appId, userId)
      case _ =>
        throw IllegalArgumentException(f"Unknown path $pathName!")

/** The app selection menu, where the user can create a new app. */
object HomePage extends Component:
  val classList = "HomePage"
  def renderInto(target: Element) =
    Requests.listApps.map: response =>
      AppCreationPage(response.apps).renderInto(target)

case class AppCreationPage(apps: Seq[String]) extends Component:
  val classList = "AppCreationPage"

  def submit(e: dom.Event): Unit =
    e.preventDefault()
    val appName = getElementById[Select]("app-name").value
    val userIdStr = getElementById[Input]("user-ids").value
    val userIds = userIdStr.split("[;,]").map(_.strip).to(Seq).distinct
    Requests.createApp(appName, userIds).map: resp =>
      WebClient.navigateTo(JoinPageLoader(appName, resp.appId))

  def renderInto(target: Element) = replaceChildren(target):
    frag(
      pageHeader("Create a new app instance"),
      form(
        onsubmit := submit,
        div(
          cls := "grid-form",
          label(`for` := "app-name", "App: "),
          select(id := "app-name", name := "app", required := true):
            apps.map(appName => option(value := appName, appName))
          ,
          label(`for` := "user-ids", "User IDs: "),
          input(
            `type` := "text",
            id := "user-ids",
            placeholder := "user1; user2; …",
            required := true
          )
        ),
        input(`type` := "submit", value := "Start!")
      )
    )

/** The pre-connection menu, which fetches the user list. */
case class JoinPageLoader(appName: String, appId: AppInstanceId) extends Component:
  val classList = "JoinPageLoader"
  def renderInto(target: Element) =
    Requests.appInfo(appId).map: resp =>
      JoinPage(appName, appId, resp.userIds).renderInto(target)

/** The connection menu, where a user joins an existing app. */
case class JoinPage(appName: String, appId: AppInstanceId, userIds: Seq[UserId])
    extends Component:
  val classList = "JoinPage"

  private def cssId(idx: Int) = f"user-$idx"

  private def getSelected =
    userIds.zipWithIndex.find { (u, i) =>
      getElementById[Input](cssId(i)).checked
    }.map(_._1)

  def submit(e: dom.Event): Unit =
    e.preventDefault()
    getSelected.map: userId =>
      WebClient.navigateTo(AppPage(appName, appId, userId))

  def renderInto(target: Element) = replaceChildren(target):
    frag(
      pageHeader("Join app instance"),
      form(
        onsubmit := submit,
        fieldset(
          legend("Select your username"),
          for (userId, idx) <- userIds.zipWithIndex
          yield div(
            input(
              `type` := "radio",
              id := cssId(idx),
              if idx == 0 then checked := "checked" else frag(),
              name := "user",
              value := userId,
              required := true
            ),
            label(`for` := cssId(idx), userId)
          )
        ),
        input(`type` := "submit", value := "Join!")
      )
    )

case class AppPage(appName: String, appId: AppInstanceId, userId: UserId) extends Component:
  val classList = f"app $appName"

  val app: ClientApp =
    WebClient.getApp(appName).get

  def renderInto(target: Element) =
    replaceChildren(target):
      frag(header(id := "banner"), tag("section")(id := "app"))
    Requests.appInfo(appId).map: appInfo =>
      val hostName = dom.window.location.hostname
      val endpoint = appInfo.wsEndpoint
        .replace("{{hostName}}", hostName)
        .replace("{{userId}}", URLEncoder.encode(userId, "UTF-8"))
      IpBanner(appInfo.shareUrl).renderInto(target.querySelector("#banner"))
      app.init(appId, userId, endpoint, target.querySelector("#app"))

case class IpBanner(shareUrl: String) extends Component:
  val classList = "IpBanner"

  def renderInto(target: Element) =
    replaceChildren(target):
      span("Share ", a(href := shareUrl, shareUrl), " with your friends to let them join!")

def getElementById[T](id: String) =
  dom.document.getElementById(id).asInstanceOf[T]
