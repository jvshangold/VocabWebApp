package cs214.webapp.server

import ujson.Value
import scala.util.Try

import cs214.webapp.*
import cs214.webapp.messages.*

/** Wraps a state machine into a server-side app. */
case class StateMachineServerApp[E, S, V](sm: StateMachine[E, S, V]) extends ServerApp:
  def name = sm.name

  def init(clients: Seq[UserId]): ServerAppInstance =
    StateMachineServerAppInstance(sm = sm, registeredUsers = clients, state = sm.init(clients))

/** Wraps a state machine and its state into a server-side app instance. */
case class StateMachineServerAppInstance[E, S, V](sm: StateMachine[E, S, V], registeredUsers: Seq[UserId], state: S)
    extends ServerAppInstance:
  val debug = false

  val name = sm.name

  def projectFor(userId: UserId)(actions: Seq[Action[S]]): Seq[Action[V]] =
    actions.map(_.map(st => sm.project(st)(userId)))

  override def transition(clients: Seq[UserId])(
      uid: UserId,
      event_js: ujson.Value
  ): Try[(Map[UserId, Seq[Action[Value]]], ServerAppInstance)] = Try:
    val event = sm.wire.eventFormat.decode(event_js).get
    if debug then
      println(f"Got event: $event")

    val actions = sm.transition(state)(uid, event).get
    if debug then
      println(s"Transition produced actions ${actions.mkString(", ")}")

    val clientActions = clients.map: uid =>
      uid -> projectFor(uid)(actions).map(_.map(sm.wire.viewFormat.encode))
    val nextState =
      actions.collect { case Action.Render(t) => t }.lastOption.getOrElse(state)

    (clientActions.toMap, copy(state = nextState))

  override def respondToNewClient(userId: UserId): Value =
    sm.wire.viewFormat.encode(sm.project(state)(userId))
