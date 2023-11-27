package cs214.webapp

/// Configuration

object Config:
  /** Which port the websocket server uses */
  val WS_PORT = 9090

  /** Which port the HTTP server uses */
  val HTTP_PORT = 8080

/// Type aliases

type AppInstanceId = String
type UserId = String

/// Encoding and decoding

/** Encodes an object of type [[T]] to a [[ujson.Value]] */
trait Encoder[T]:
  def encode(t: T): ujson.Value

/** Decodes an object of type [[T]] from a [[ujson.Value]] */
trait Decoder[T]:
  def decode(json: ujson.Value): util.Try[T]

/** Provides a way to decode and encode an object of type [[T]] to [[Value]] */
trait WireFormat[T] extends Encoder[T] with Decoder[T]

/// APIs

object Endpoints:
  sealed abstract case class Api(path: String):
    val root = "/api"
    override def toString = f"$root/$path"

  object Api:
    /** Returns the server's IP. */
    object ip extends Api("ip")
    object listApps extends Api("list-apps")
    object initializeApp extends Api("initialize-app")
    object appInfo extends Api("app-info")

/// App registration

trait RegistrationProvider:
  def register(): Unit

/// TODOs
/** Using ??? in a val breaks worksheet evaluation, so below we use TODO
  * instead. This TODO value is a hack!
  */
def TODO[A]: A = null.asInstanceOf[A]
