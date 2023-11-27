package driver

import cs214.webapp.server.WebServer

object MainJVM:
  @main def main() =
    WebServer.register("tictactoe")
    WebServer.register("memory")
    WebServer.start()
