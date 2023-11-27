package cs214.webapp
package client

import org.scalajs.dom
import scalatags.JsDom.all.{Frag}
import ujson.*

import scala.collection.mutable
import scala.scalajs.js.timers.{SetTimeoutHandle, setTimeout}
import scala.util.{Failure, Success}

import messages.*

abstract class WSClientApp extends ClientApp:
  protected def init(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element): ClientAppInstance

  def init(appId: AppInstanceId, userId: UserId, endpoint: String, target: dom.Element): ClientAppInstance =
    val socket = new dom.WebSocket(endpoint)
    socket.onopen = (event: dom.Event) => println("WebSocket connection opened")
    socket.onclose = (event: dom.CloseEvent) => println(s"WebSocket connection closed: ${event.reason}")
    socket.onerror = (event: dom.Event) => println(s"WebSocket error: ${event.`type`}")

    val sendMessage = (js: ujson.Value) => socket.send(js.toString)
    val client = init(userId, sendMessage, target)
    socket.onmessage = msg =>
      val js = ujson.read(msg.data.toString)
      client.onMessage(SocketResponseWire.decode(js).flatten)

    client

abstract class StateMachineClientAppInstance[Event, View](
    userId: UserId,
    sendMessage: ujson.Value => Unit,
    target: dom.Element
) extends ClientAppInstance:
  /** Provides serialization methods for events and views */
  val wire: AppWire[Event, View]

  /** Renders a [[View]] received from the server. The method also takes a
    * [[UserId]] to get information on the context and an [[onEvent]] callback
    * which it uses to send server events when specific actions are triggered on
    * the UI (click on a button for example)
    * @param userId
    *   The user id used by the client.
    * @param view
    *   The view to render.
    * @return
    *   The rendered view.
    */
  def render(userId: UserId, view: View): Frag

  /** Renders a non-fatal error as a browser alert */
  private def renderError(msg: String): Unit =
    dom.window.alert(msg)

  protected def sendEvent(event: Event) =
    sendMessage(wire.eventFormat.encode(event))

  /** @inheritdoc */
  override def onMessage(msg: util.Try[ujson.Value]): Unit =
    msg.flatMap(EventResponse.Wire.decode) match
      case Failure(msg)              => WebClient.crash(Exception(msg))
      case Success(Failure(msg))     => renderError(msg.getMessage)
      case Success(Success(actions)) => actionsQueue.enqueueAll(actions)
    tryProcessNextAction()

  /** The views to render */
  private val actionsQueue: mutable.Queue[messages.Action[Value]] = mutable.Queue()

  /** Minimal cooldown time between the render of two views */
  private var actionCooldownDelay = 0

  /** The timestamp for the last render of a view */
  private var lastActionAppliedMs = System.currentTimeMillis()

  /** A call to the [[tryProcessNextAction()]] function scheduled */
  private var timeout: Option[SetTimeoutHandle] = None

  /** Sets the [[actionCooldownDelay]] of the client to the specified value */
  private def setCooldown(ms: Int): Unit = actionCooldownDelay = ms

  /** Apply the next action from [[actionsQueue]] if [[actionCooldownDelay]]
    * permits it.
    */
  private def tryProcessNextAction(): Unit =
    // If there are still views to render and if no call to this function is scheduled
    if actionsQueue.nonEmpty && timeout.isEmpty then
      // Then check if we are out of the cooldown delay
      if System.currentTimeMillis() - lastActionAppliedMs > actionCooldownDelay then
        // We can render the view, and dequeue it
        processAction(actionsQueue.dequeue())
        // Continue try emptying the queue
        tryProcessNextAction()
      else
        // We still have to wait, put a timeout to call the function later
        timeout = Some(setTimeout(actionCooldownDelay) {
          // First remove the timeout so that, if necessary,
          // the next function call can create a new one
          timeout = None
          // Then try to render next views
          tryProcessNextAction()
        })

  /** Execute a single action sent by the server. */
  private def processAction(jsonAction: Action[Value]): Unit =
    lastActionAppliedMs = System.currentTimeMillis()
    setCooldown(0)
    jsonAction match
      case Action.Alert(msg) =>
        dom.window.alert(msg)
      case Action.Pause(durationMs) =>
        setCooldown(durationMs)
      case Action.Render(js) =>
        // Step1: The client receives the view sent by the server here
        wire.viewFormat.decode(js) match
          case Failure(exception) => renderError(exception.getMessage)
          case Success(jsonView) =>
            target.replaceChildren:
              this.render(userId, jsonView).render
