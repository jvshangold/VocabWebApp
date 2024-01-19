lazy val vocab = (crossProject(JVMPlatform, JSPlatform) in file("."))
  .settings(name := "vocab", scalaVersion := "3.3.1")
  .jsSettings(test / aggregate := false, Test / test := {}, Test / testOnly := {})

lazy val client = project in file("./../../lib")
lazy val vocabJS = vocab.js.dependsOn(client)

lazy val server = project in file("./../../lib")
lazy val vocabJVM = vocab.jvm.dependsOn(server)
