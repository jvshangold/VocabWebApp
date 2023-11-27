package driver

import org.scalajs.dom
import cs214.webapp.client.WebClient

object MainJS:
  @main def main() =
    WebClient.start(dom.document.getElementById("root"))
