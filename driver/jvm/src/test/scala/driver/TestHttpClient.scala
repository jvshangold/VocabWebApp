package driver

import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{ConnectException, URI}

class TestHttpClient(
    port: Int
):
  private val MAX_RETRIES = 8
  private val client: HttpClient = HttpClient.newHttpClient()

  private def checkConnection1(): Boolean =
    try
      val req = HttpRequest.newBuilder(URI(s"http://localhost:$port/api/ping")).GET().build()
      val res = client.send(req, BodyHandlers.ofString())
      res.statusCode() == 200
    catch
      case e: ConnectException => /* Ignored, will just retry later */
        false

  /** Blocks until the http client can establish a connection to the http
    * server.
    */
  def checkConnection(): Unit =
    var retries = 0
    while !checkConnection1() do
      if retries >= MAX_RETRIES then
        throw ConnectException("Could not connect.")
      retries += 1
      Thread.sleep(500)

  def send(req: HttpRequest): HttpResponse[String] =
    client.send(req, BodyHandlers.ofString())
