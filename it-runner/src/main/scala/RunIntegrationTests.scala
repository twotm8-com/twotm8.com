package twotm8
package tests.integration

import weaver.*

import cats.effect.*
import fs2.Stream
import weaver.Runner.Outcome
import scala.util.control.NoStackTrace

object RunIntegrationTests extends IOApp:
  val MaxParallelSuites = 5
  val Logger = [A] => (a: A) => IO.consoleForIO.errorln[A](a)
  def suites(url: Option[String]) = Vector(Twots(url), Auth(url))

  def run(args: List[String]) =
    IO.fromOption(args.headOption)(
      new RuntimeException(
        "Please pass a URL to a Twotm8 installation (for example staging, https://twotm8-web-staging.fly.dev/)"
      ) with NoStackTrace
    ).map(Some(_))
      .flatMap { url =>
        Runner[IO](args = args.tail, maxConcurrentSuites = MaxParallelSuites)(
          Logger[String]
        )
          .run(fs2.Stream.emits(suites(url)))
          .flatMap {
            case o @ Outcome(successes, ignored, cancelled, failures) =>
              Logger(o.formatted).as {
                if failures > 0 then ExitCode.Error
                else ExitCode.Success

              }
          }
      }
end RunIntegrationTests
