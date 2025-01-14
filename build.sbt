import bindgen.plugin.BindgenMode
import bindgen.interface.Binding
import scala.scalanative.build.Mode
import scala.scalanative.build.LTO
import scala.sys.process
import java.nio.file.Paths

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    homepage := Some(url("https://github.com/twotm8-com/twotm8.com")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "indoorvivants",
        "Anton Sviridov",
        "contact@indoorvivants.com",
        url("https://blog.indoorvivants.com")
      )
    ),
    version := (if (!sys.env.contains("CI")) "dev" else version.value),
    crossScalaVersions := Nil
  )
)

organization := "com.indoorvivants.twotm8"
sonatypeProfileName := "com.indoorvivants"

lazy val publishing = Seq(
  organization := "com.indoorvivants.twotm8",
  sonatypeProfileName := "com.indoorvivants"
)

lazy val noPublish = Seq(publish / skip := true, publishLocal / skip := true)

lazy val root = project
  .in(file("."))
  .aggregate(frontend.projectRefs*)
  .aggregate(app.projectRefs*)
  .aggregate(shared.projectRefs*)
  .aggregate(tests.projectRefs*)
  .aggregate(client.projectRefs*)
  .settings(noPublish)

lazy val shared =
  projectMatrix
    .in(file("shared"))
    .defaultAxes(Axes.shared*)
    .jvmPlatform(Seq(Versions.Scala))
    .jsPlatform(Seq(Versions.Scala))
    .nativePlatform(Seq(Versions.Scala))
    .settings(
      moduleName := "twotm8-shared",
      scalaVersion := Versions.Scala,
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %%% "tapir-json-upickle" % Versions.Tapir,
        "com.softwaremill.sttp.tapir" %%% "tapir-core" % Versions.Tapir
      ),
      publishing
    )

lazy val frontend =
  projectMatrix
    .in(file("frontend"))
    .defaultAxes(Axes.frontend*)
    .jsPlatform(Seq(Versions.Scala))
    .settings(
      scalaJSUseMainModuleInitializer := true,
      scalaVersion := Versions.Scala,
      libraryDependencies ++= Seq(
        "com.github.japgolly.scalacss" %%% "core" % Versions.scalacss,
        "com.lihaoyi" %%% "upickle" % Versions.upickle,
        "com.raquo" %%% "laminar" % Versions.Laminar,
        "com.raquo" %%% "waypoint" % Versions.waypoint,
        "com.softwaremill.retry" %%% "retry" % Versions.sttpRetry,
        "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % Versions.Tapir,
        "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
        "org.scala-js" %%% "scala-js-macrotask-executor" % Versions.macroTaskExecutor
      ),
      noPublish
    )
    .dependsOn(shared)

lazy val tests =
  projectMatrix
    .in(file("tests"))
    .defaultAxes(Axes.shared*)
    .dependsOn(shared)
    .jvmPlatform(
      Seq(Versions.Scala),
      Seq.empty,
      _.settings(
        libraryDependencies ++= Seq(
          "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % Versions.Tapir % Test,
          "org.http4s" %% "http4s-ember-client" % Versions.Http4s % Test,
          "com.github.jwt-scala" %% "jwt-upickle" % Versions.jwt % Test
        )
      )
    )
    .nativePlatform(
      Seq(Versions.Scala),
      Seq.empty,
      configure = { proj =>
        proj
          .dependsOn(app.native(Versions.Scala))
          .enablePlugins(VcpkgNativePlugin)
          .settings(
            vcpkgDependencies := VcpkgDependencies("openssl"),
            libraryDependencies +=
              "com.github.lolgab" %%% "scala-native-crypto" % Versions.scalaNativeCrypto % Test,
            nativeConfig ~= { _.withIncrementalCompilation(true) }
          )

      }
    )
    .settings(
      libraryDependencies ++= Seq(
        // TODO. Not supported yet on Scala Native 0.5
        // "com.disneystreaming" %%% "weaver-cats" % Versions.weaver % Test
      ),
      testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
      noPublish
    )

lazy val app =
  projectMatrix
    .in(file("app"))
    .defaultAxes(Axes.native*)
    .nativePlatform(Seq(Versions.Scala))
    .dependsOn(shared)
    .enablePlugins(VcpkgNativePlugin)
    .settings(environmentConfiguration)
    .settings(
      scalaVersion := Versions.Scala,
      vcpkgDependencies := VcpkgDependencies(
        (ThisBuild / baseDirectory).value / "vcpkg.json"
      ),
      libraryDependencies ++= Seq(
        "com.github.lolgab" %%% "scala-native-crypto" % Versions.scalaNativeCrypto,
        "com.github.lolgab" %%% "snunit-tapir" % Versions.SNUnit,
        "com.indoorvivants.roach" %%% "core" % Versions.Roach,
        "com.lihaoyi" %%% "upickle" % Versions.upickle,
        "com.outr" %%% "scribe" % Versions.scribe
      ),
      Compile / resources ~= { _.filter(_.ext == "sql") },
      nativeConfig ~= (_.withEmbedResources(true)
        .withIncrementalCompilation(true)),
      noPublish
    )

lazy val client =
  projectMatrix
    .in(file("client"))
    .defaultAxes(Axes.native*)
    .nativePlatform(Seq(Versions.Scala))
    .dependsOn(shared)
    .enablePlugins(VcpkgNativePlugin, BindgenPlugin)
    .settings(environmentConfiguration)
    .settings(
      moduleName := "twotm8-client",
      scalaVersion := Versions.Scala,
      vcpkgDependencies := VcpkgDependencies(
        "curl",
        "libidn2"
      ),
      vcpkgNativeConfig ~= { _.addRenamedLibrary("curl", "libcurl") },
      libraryDependencies ++= Seq(
        // TODO. Not supported yet on Scala Native 0.5
        // "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % Versions.Tapir
      ),
      bindgenBindings +=
        Binding(
            vcpkgConfigurator.value.includes("curl") / "curl" / "curl.h",
            "curl"
          )
          .addCImport("curl/curl.h")
          .withNoLocation(true),
      bindgenMode := BindgenMode.Manual(
        sourceDirectory.value / "main" / "scala" / "generated",
        (Compile / resourceDirectory).value / "scala-native"
      ),
      nativeConfig ~= (_.withIncrementalCompilation(true)),
      publishing
    )

lazy val itRunner = projectMatrix
  .in(file("it-runner"))
  .defaultAxes(Axes.jvm*)
  .jvmPlatform(Seq(Versions.Scala))
  .dependsOn(tests % "compile->test")

addCommandAlias("runIntegrationTests", "itRunner/run")
addCommandAlias(
  "localIntegrationTests",
  "runIntegrationTests http://localhost:8080"
)
addCommandAlias(
  "stagingIntegrationTests",
  "itRunner/run https://twotm8-web-staging.fly.dev/"
)

addCommandAlias("nativeTests", "testsNative/test")

lazy val Axes = new {
  val jvm = Seq(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(Versions.Scala))
  val native =
    Seq(VirtualAxis.scalaABIVersion(Versions.Scala), VirtualAxis.native)
  val frontend =
    Seq(VirtualAxis.scalaABIVersion(Versions.Scala), VirtualAxis.js)
  val shared = Seq(VirtualAxis.scalaABIVersion(Versions.Scala))
}

val Versions = new {
  val Scala = "3.5.2"

  val SNUnit = "0.10.3"

  val Tapir = "1.11.12"

  val upickle = "3.3.1"

  val scribe = "3.16.0"

  val Laminar = "16.0.0"

  val scalajsDom = "2.6.0"

  val waypoint = "7.0.0"

  val scalacss = "1.0.0"

  val Roach = "0.1.0"

  val sttpRetry = "0.3.6"

  val scalaNativeCrypto = "0.2.0"

  val weaver = "0.8.3"

  val Http4s = "0.23.23"

  val jwt = "9.4.4"

  val macroTaskExecutor = "1.1.1"
}

lazy val environmentConfiguration = Seq(nativeConfig := {
  val conf = nativeConfig.value
  if (sys.env.get("SN_RELEASE").contains("fast"))
    conf.withOptimize(true).withLTO(LTO.thin).withMode(Mode.releaseFast)
  else conf
})

val buildApp = taskKey[Unit]("")
buildApp := {
  buildBackend.value
  buildFrontend.value
}

val buildBackend = taskKey[Unit]("")
buildBackend := {
  val target = (app.native(Versions.Scala) / Compile / nativeLink).value

  val destination = (ThisBuild / baseDirectory).value / "build" / "twotm8"

  IO.copyFile(
    target,
    destination,
    preserveExecutable = true,
    preserveLastModified = true
  )

  process.Process(s"chmod 0777 ${destination}").!!

  sys.env.get("CI").foreach { _ =>
    val sudo = if (sys.env.contains("USE_SUDO")) "sudo " else ""
    /* process.Process(s"${sudo}chown unit ${destination}").!! */
    /* process.Process(s"${sudo}chgrp unit ${destination}").!! */
  }
}

def unitConfig(buildPath: File) =
  s"""
{
  "listeners": {
    "*:8080": {
      "pass": "routes"
    }
  },
  "routes": [
    {
      "match": {
        "uri": "/api/*"
      },
      "action": {
        "pass": "applications/app"
      }
    },
    {
      "match": {
        "uri": "~^((/(.*)\\\\.(js|css|html))|/)$$"
      },
      "action": {
        "share": "${buildPath}$$uri"
      }
    },
    {
      "action": {
        "share": "${buildPath / "index.html"}"
      }
    }
  ],
  "applications": {
    "app": {
      "processes": {
        "max": 50,
        "spare": 2,
        "idle_timeout": 180
      },
      "type": "external",
      "executable": "${buildPath / "twotm8"}",
      ${sys.env
      .get("CI")
      .map { _ =>
        """
        "user": "runner",
        "group": "docker",
        """
      }
      .getOrElse("")}
      "environment": {
        "JWT_SECRET": "secret"
      },
      "limits": {
        "timeout": 1,
        "requests": 1000
      }
    }
  }
}

"""

lazy val deployLocally = taskKey[Unit]("")
deployLocally := {
  locally { buildApp.value }
  locally { updateUnitConfiguration.value }
}

lazy val updateUnitConfiguration = taskKey[Unit]("")

updateUnitConfiguration := {
  sLog.value.info(buildBackend.value.toString)

  val configJson = writeConfig.value

  val sudo = if (sys.env.contains("USE_SUDO")) "sudo " else ""

  val cmd_create =
    s"${sudo}unitc /config"
  val cmd =
    s"${sudo}unitc /control/applications/app/restart"

  val create_result = process.Process(cmd_create).#<(configJson).!!
  val reload_result = process.Process(cmd).!!

  assert(
    create_result.contains("Reconfiguration done"),
    s"Unit reconfiguration didn't succeed, returning `$create_result`"
  )
  assert(
    reload_result.contains("success"),
    s"Unit reload didn't succeed, returning `$reload_result`"
  )
}

lazy val writeConfig = taskKey[File]("")
writeConfig := {
  val buildPath = (ThisBuild / baseDirectory).value / "build"
  val path = buildPath / "config.json"

  IO.write(path, unitConfig(buildPath))

  path
}

lazy val frontendFile = taskKey[File]("")
frontendFile := {
  if (sys.env.get("SN_RELEASE").contains("fast"))
    (frontend.js(Versions.Scala) / Compile / fullOptJS).value.data
  else
    (frontend.js(Versions.Scala) / Compile / fastOptJS).value.data
}

lazy val buildFrontend = taskKey[Unit]("")
buildFrontend := {
  val js = frontendFile.value
  val destination = (ThisBuild / baseDirectory).value / "build"

  IO.write(
    destination / "index.html",
    """
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <meta http-equiv="X-UA-Compatible" content="ie=edge">
          <title>Twotm8 - a place for thought leaders to thought lead</title>
        </head>
        <body>
        <div id="appContainer"></div>
        <script src="/frontend.js"></script>
        </body>
      </html>
    """.stripMargin
  )

  IO.copyFile(js, destination / "frontend.js")
}
