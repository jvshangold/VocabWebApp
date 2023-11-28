lazy val memory = (crossProject(JVMPlatform, JSPlatform) in file("."))
  .settings(name := "memory", scalaVersion := "3.3.1")
  .jsSettings(test / aggregate := false, Test / test := {}, Test / testOnly := {})

lazy val client = project in file("./../../lib")
lazy val memoryJS = memory.js.dependsOn(client)

lazy val server = project in file("./../../lib")
lazy val memoryJVM = memory.jvm.dependsOn(server)
