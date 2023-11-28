lazy val ticTacToe = (crossProject(JVMPlatform, JSPlatform) in file("."))
  .settings(name := "ticTacToe", scalaVersion := "3.3.1")
  .jsSettings(test / aggregate := false, Test / test := {}, Test / testOnly := {})

lazy val client = project in file("./../../lib")
lazy val ticTacToeJS = ticTacToe.js.dependsOn(client)

lazy val server = project in file("./../../lib")
lazy val ticTacToeJVM = ticTacToe.jvm.dependsOn(server)
