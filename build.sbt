import bindgen.plugin.BindgenMode
import bindgen.interface.Binding
import scala.scalanative.build.Mode
import scala.scalanative.build.LTO
import scala.sys.process
import java.nio.file.Paths
import sys.process.*

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
  .aggregate(bindings.projectRefs*)
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
        "com.disneystreaming" %%% "weaver-cats" % Versions.weaver % Test
      ),
      testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
      noPublish
    )

lazy val app =
  projectMatrix
    .in(file("app"))
    .defaultAxes(Axes.native*)
    .nativePlatform(Seq(Versions.Scala))
    .dependsOn(bindings, shared)
    .enablePlugins(VcpkgNativePlugin)
    .settings(
      scalaVersion := Versions.Scala,
      vcpkgDependencies := VcpkgDependencies(
        (ThisBuild / baseDirectory).value / "vcpkg.json"
      ),
      libraryDependencies ++= Seq(
        "com.github.lolgab" %%% "scala-native-crypto" % Versions.scalaNativeCrypto % Test,
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

lazy val bindings =
  projectMatrix
    .in(file("bindings"))
    .defaultAxes(Axes.native*)
    .nativePlatform(Seq(Versions.Scala))
    .enablePlugins(BindgenPlugin, VcpkgPlugin)
    .settings(
      scalaVersion := Versions.Scala,
      resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
      // Generate bindings to Postgres main API
      vcpkgDependencies := VcpkgDependencies("openssl"),
      Compile / bindgenBindings ++= Seq(
        Binding
          .builder(
            (ThisBuild / baseDirectory).value / "bindings" / "openssl-amalgam.h",
            "openssl"
          )
          .withCImports(List("openssl/sha.h", "openssl/evp.h"))
          .addClangFlag("-I" + vcpkgConfigurator.value.includes("openssl"))
          .addClangFlag("-fsigned-char")
          .build
      ),
      bindgenMode := BindgenMode.Manual(
        scalaDir = sourceDirectory.value / "main" / "scalanative" / "generated",
        cDir = (Compile / resourceDirectory).value / "scala-native"
      ),
      noPublish
    )

lazy val client =
  projectMatrix
    .in(file("client"))
    .defaultAxes(Axes.native*)
    .nativePlatform(Seq(Versions.Scala))
    .dependsOn(shared)
    .enablePlugins(VcpkgNativePlugin, BindgenPlugin)
    .settings(
      moduleName := "twotm8-client",
      scalaVersion := Versions.Scala,
      vcpkgDependencies := VcpkgDependencies(
        "curl",
        "libidn2"
      ),
      vcpkgNativeConfig ~= { _.addRenamedLibrary("curl", "libcurl") },
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % Versions.Tapir
      ),
      bindgenBindings +=
        Binding
          .builder(
            vcpkgConfigurator.value.includes("curl") / "curl" / "curl.h",
            "curl"
          )
          .addCImport("curl/curl.h")
          .withNoLocation(true)
          .build,
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
  .settings(fork := true)

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
  val Scala = "3.3.1"

  val SNUnit = "0.7.2"

  val Tapir = "1.9.0"

  val upickle = "3.1.3"

  val scribe = "3.12.2"

  val Laminar = "16.0.0"

  val scalajsDom = "2.6.0"

  val waypoint = "7.0.0"

  val scalacss = "1.0.0"

  val Roach = "0.0.6"

  val sttpRetry = "0.3.6"

  val scalaNativeCrypto = "0.0.4"

  val weaver = "0.8.3"

  val Http4s = "0.23.23"

  val jwt = "9.4.4"

  val macroTaskExecutor = "1.1.1"
}

val buildApp = taskKey[Unit]("")
buildApp := {
  buildBackend.value
  buildFrontend.value
}

lazy val buildBackend = taskKey[File]("")
ThisBuild / buildBackend := {
  val dest = (ThisBuild / baseDirectory).value / "build"
  val statedir = dest / "statedir"
  IO.createDirectory(statedir)
  val serverBinary = (app.native(Versions.Scala) / Compile / nativeLink).value

  IO.copyFile(serverBinary, dest / "server")
  IO.copyFile(dest.getParentFile() / "conf.json", statedir / "conf.json")

  dest
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
  val staticdir =
    (ThisBuild / baseDirectory).value / "build" / "static"
  IO.createDirectory(staticdir)

  IO.write(
    staticdir / "index.html",
    """
    |<!DOCTYPE html>
    |<html lang="en">
    |  <head>
    |    <meta charset="UTF-8">
    |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    |    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    |    <title>Twotm8 - a place for thought leaders to thought lead</title>
    |  </head>
    |  <body>
    |  <div id="appContainer"></div>
    |  <script src="/frontend.js"></script>
    |  </body>
    |</html>
    """.stripMargin.trim
  )

  IO.copyFile(js, staticdir / "frontend.js")
}

def UNITD_LOCAL_COMMAND =
  "unitd --statedir statedir --log /dev/stderr --no-daemon --control 127.0.0.1:9933"

lazy val runServer = taskKey[Unit]("")
runServer := {
  val dest = buildBackend.value

  val proc = Process(UNITD_LOCAL_COMMAND, cwd = dest)

  proc.!
}

lazy val devServer = project
  .in(file("dev-server"))
  .enablePlugins(RevolverPlugin)
  .settings(
    fork := true,
    scalaVersion := Versions.Scala,
    envVars ++= Map(
      "TWOTM8_SERVER_BINARY" -> (ThisBuild / buildBackend).value.toString,
      "TWOTM8_UNITD_COMMAND" -> UNITD_LOCAL_COMMAND,
      "TWOTM8_SERVER_CWD" -> ((ThisBuild / baseDirectory).value / "build").toString,
      "JWT_SECRET" -> "helloworld"
    )
  )
