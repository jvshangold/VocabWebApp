package cs214.webapp

import ujson.Value
import scala.util.Try

import messages.Action

/** A state machine describes the core logic of an application.
  *
  * @tparam Event
  *   The type of the events that this application expects.
  * @tparam State
  *   The type of internal states of this application.
  * @tparam View
  *   The type of the views sent to the clients.
  */
abstract class StateMachine[Event, State, View]:
  /** An identifier for this app type. */
  val name: String

  /** Provides serialization methods for events and views. */
  val wire: AppWire[Event, View]

  /** Initializes a new application. */
  def init(clients: Seq[UserId]): State

  /** Simulates a transition of the state machine.
    *
    * @param state
    *   The current [[State]] of the application.
    * @param userId
    *   The [[UserId]] of the user triggering the event
    * @param event
    *   The [[Event]] received
    * @return
    *   A sequence of commands to be sent to clients, or [[scala.util.Failure]]
    *   if the event is illegal given the current state..
    */
  def transition(state: State)(userId: UserId, event: Event): Try[Seq[Action[State]]]

  /** Projects an application state to produce a user-specific view.
    *
    * This function hides any details of the application's state that the user
    * should not have access to.
    *
    * @param state
    *   A [[State]] of the application.
    * @param userId
    *   The [[UserId]] of a user.
    * @return
    *   A view for user [[uid]].
    */
  def project(state: State)(userId: UserId): View

/** The ServerApp interface is used by the server to create new webapp
  * instances.
  */
trait ServerApp:
  def name: String
  def init(clients: Seq[UserId]): ServerAppInstance

/** Provides all the necessary encoding/decoding methods for an application. */
trait AppWire[Event, View]:
  val eventFormat: WireFormat[Event]
  val viewFormat: WireFormat[View]

/** The ServerAppInstance interface is used by the server to store the state of
  * an app.
  *
  * Note that this interface is *pure*: the [[transition]] function creates a
  * new ServerApp.
  */
trait ServerAppInstance:
  val name: String
  val registeredUsers: Seq[String]

  /** Compute setup actions to send to client. */
  def respondToNewClient(userId: UserId): ujson.Value

  /** Simulate one step of the underlying state machine. */
  def transition(clients: Seq[UserId])(
      userId: UserId,
      jsEvent: Value
  ): Try[(Map[UserId, Seq[Action[Value]]], ServerAppInstance)]
