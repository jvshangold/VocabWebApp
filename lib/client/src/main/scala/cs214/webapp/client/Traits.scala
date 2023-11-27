package cs214.webapp
package client

import org.scalajs.dom

/** The ClientApp interface is used by clients to connect to a server. */
trait ClientApp:
  def name: String
  def init(appId: AppInstanceId, userId: UserId, endpoint: String, target: dom.Element): ClientAppInstance

/** The ClientAppInstance interface is used by the server to store the state of
  * a client UI.
  */
trait ClientAppInstance:
  def name: String
  def onMessage(msg: util.Try[ujson.Value]): Unit
