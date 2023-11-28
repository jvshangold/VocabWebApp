import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.Keys.libraryDependencies

lazy val shared = crossProject(JSPlatform, JVMPlatform).in(file("./shared"))
  .jsConfigure(_.enablePlugins(JSDependenciesPlugin))
  .settings(
    name := "shared",
    scalaVersion := "3.3.1",
    libraryDependencies += dependencies.ujson,
    scalacOptions ++= Seq("-deprecation")
  ).jsSettings(
    test / aggregate := false,
    Test / test := {},
    Test / testOnly := {}
  ).jvmSettings(
  )

lazy val client = (project in file("./client"))
  .dependsOn(shared.js)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "client",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.3.1",
    scalacOptions ++= Seq("-deprecation"),
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.lihaoyi" %%% "scalatags" % "0.12.0",
    ),
    // Add support for the DOM in `run` and `test`
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    test / aggregate := false,
    Test / test := {},
    Test / testOnly := {}
  )

lazy val server = (project in file("./server"))
  .dependsOn(shared.jvm)
  .settings(
    name := "server",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.3.1",
    scalacOptions ++= Seq("-deprecation"),
    libraryDependencies ++= Seq(
      dependencies.websocket,
      dependencies.http4sCircle,
      dependencies.http4sDsl,
      dependencies.http4sEmberServer,
      dependencies.circle,
      dependencies.slf4j,
    ),
  )

/// Dependencies

lazy val dependencies = new {
  val ujson = "com.lihaoyi" %% "ujson" % "3.1.3"
  val websocket = "org.java-websocket" % "Java-WebSocket" % "1.5.4"
  val http4sVersion = "1.0.0-M39"
  val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion
  val http4sCircle = "org.http4s" %% "http4s-circe" % http4sVersion
  val circle = "io.circe" %% "circe-generic" % "0.14.1"
  val slf4j = "org.slf4j" % "slf4j-nop" % "2.0.0"
}
