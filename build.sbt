import scala.util.control.Exception.noCatch.andFinally

/// Commands

lazy val copyJsTask = TaskKey[Unit]("copyJsTask", "Copy javascript files to server target directory")
lazy val copyTestReportsTask = TaskKey[Unit]("copyTestReportsTask", "Copy test reports files to root target directory")

/// Apps

lazy val ticTacToeJS = project in file("./apps/ticTacToe")
lazy val ticTacToeJVM = project in file("./apps/ticTacToe")

lazy val memoryJS = project in file("./apps/memory")
lazy val memoryJVM = project in file("./apps/memory")

/// Tests

lazy val driver = (crossProject(JVMPlatform, JSPlatform) in file("./driver"))
  .settings(
    name := "driver",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.3.1",
    scalacOptions ++= Seq("-deprecation")
  ).jsSettings(
    Compile / mainClass := Some("driver.main"),
    scalaJSUseMainModuleInitializer := true,
    test / aggregate := false,
    Test / test := {},
    Test / testOnly := {},
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "ujson" % "3.1.3",
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.lihaoyi" %%% "scalatags" % "0.12.0",
    )
  ).jvmSettings(
    Compile / mainClass := Some("driver.main"),
    run / fork := true,
    Global / cancelable := true,
    libraryDependencies ++= Seq(
      "org.java-websocket" % "Java-WebSocket" % "1.5.4",
      "org.scala-lang" %% "toolkit-test" % "0.1.7" % Test,
      "com.lihaoyi" %% "ujson" % "3.1.3",
      dependencies.websocket,
      dependencies.http4sCircle,
      dependencies.http4sDsl,
      dependencies.http4sEmberServer,
      dependencies.circle,
      dependencies.slf4j,
    )
  )

lazy val driverJS = driver.js.dependsOn(ticTacToeJS, memoryJS)
lazy val driverJVM = driver.jvm.dependsOn(ticTacToeJVM, memoryJVM)

/// Aggregate project

lazy val webapp = (project in file("."))
  .aggregate(
    ticTacToeJS, ticTacToeJVM, memoryJS, memoryJVM, driverJS, driverJVM
  ).settings(
    name := "webapp",
    scalaVersion := "3.3.1",
    scalacOptions ++= Seq("-deprecation"),
    copyJsTask := {
      println("[info] Copying generated main.js to server's static files directory...")
      val inDir = baseDirectory.value / "driver/js/target/scala-3.3.1/driver-fastopt/"
      val outDir = baseDirectory.value / "lib/server/src/main/resources/www/static/"
      Seq("main.js", "main.js.map") map { p => (inDir / p, outDir / p) } foreach { f => IO.copyFile(f._1, f._2) }
    },
    copyTestReportsTask := {
      println("[info] Copying test reports to lab root directory...")
      val inDir = baseDirectory.value / "driver/jvm/target/test-reports/"
      val outDir = baseDirectory.value / "target/test-reports/"
      Seq(
        "TEST-driver.MemoryTest.xml",
        "TEST-driver.TicTacToeTest.xml"
      ) map { p => (inDir / p, outDir / p) } foreach { f => IO.copyFile(f._1, f._2) }
    },
    // FIXME: Race conditions in integration tests
    // Test / testOnly := Def.inputTaskDyn {
    //   val args: Seq[String] = Def.spaceDelimited().parsed
    //   val argsStr: String = if (args.isEmpty) "" else args.mkString(" ", " ", "")
    //   // Test setup
    //   Def.sequential(
    //     Compile / compile,
    //     Test / compile,
    //     (driverJS / Compile / fastLinkJS).toTask,
    //     copyJsTask,
    //     (driverJVM / Compile / reStart).toTask(""),
    //     // Actual tests
    //     (driverJVM / Test / testOnly).toTask(argsStr)
    //       // Test cleanup
    //       .doFinally(copyTestReportsTask.taskValue)
    //       .doFinally((driverJVM / Compile / reStop).toTask.taskValue),
    //   )
    // }.evaluated,
    // Test / test := Def.sequential(
    //   (Test / testOnly).toTask("")
    // ).value,
    driverJVM / Test / testOnly := {
      (driverJVM / Test / testOnly).parsed.doFinally(copyTestReportsTask.taskValue).value
    },
    Test / test := Def.sequential(
      (Test / testOnly).toTask("")
    ).value,
    Compile / run := Def.sequential(
      Compile / compile,
      (driverJS / Compile / fastLinkJS).toTask,
      copyJsTask,
      (driverJVM / Compile / run).toTask(""),
    ).value,
  )

/// Dependencies

lazy val dependencies =
  new {
    val http4sVersion = "1.0.0-M39"
    val circeVersion = "0.14.5"
    val ujson = "com.lihaoyi" %% "ujson" % "3.1.3"
    // Websocket dependencies
    val websocket = "org.java-websocket" % "Java-WebSocket" % "1.5.4"
    // Http dependencies
    val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
    val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion
    val http4sCircle = "org.http4s" %% "http4s-circe" % http4sVersion
    val circle = "io.circe" %% "circe-generic" % circeVersion
    val slf4j = "org.slf4j" % "slf4j-nop" % "2.0.5"
  }
