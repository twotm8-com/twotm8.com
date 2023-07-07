package twotm8
package tests.integration

import cats.effect.IO
import pdi.jwt.JwtOptions
import org.http4s.*
import org.http4s.ember.client.*
import cats.effect.*
import pdi.jwt.JwtOptions
import twotm8.api.ErrorInfo

import sttp.tapir.client.http4s.Http4sClientInterpreter

object AuthIntegrationTest extends Auth(None)

class Auth(url: Option[String] = None) extends BaseTest(url) with Fragments:
  group("registration") {
    integrationTest("success") { probe =>
      for
        nickname <- probe.generator.str(Nickname, 8 to 15)
        password <- probe.generator.string(8 to 15).map(Password(_))
        result <-
          probe.execute(
            endpoints.register,
            api.Payload.Register(nickname, password)
          )
      yield expect(result.isRight)
    }

    group("failure") {
      integrationTest("nickname already taken") { probe =>
        for
          nickname <- probe.generator.str(Nickname, 8 to 15)
          password <- probe.generator.string(8 to 15).map(Password(_))
          firstRegistration <-
            probe.execute(
              endpoints.register,
              api.Payload.Register(nickname, password)
            )

          _ <- expect(firstRegistration.isRight).failFast

          secondRegistration <-
            probe.execute(
              endpoints.register,
              api.Payload.Register(nickname, password)
            )

          _ <- expect(secondRegistration.isLeft).failFast
        yield success
      }

      integrationTest("password too short") { probe =>
        for
          nickname <- probe.generator.str(Nickname, 8 to 15)
          password <- probe.generator.string(0 to 8).map(Password(_))
          firstRegistration <-
            probe.execute(
              endpoints.register,
              api.Payload.Register(nickname, password)
            )
          _ <- expect(firstRegistration.isLeft).failFast
        yield success
      }
    }
  }

  group("login") {
    integrationTest("success") { probe =>
      for
        nickname <- probe.generator.str(Nickname, 8 to 15)
        password <- probe.generator.string(8 to 15).map(Password(_))

        result <-
          probe.execute(
            endpoints.register,
            api.Payload.Register(nickname, password)
          ) *>
            probe.execute(
              endpoints.login,
              api.Payload.Login(nickname, password)
            )

        decoded <-
          IO.fromEither(result.left.map(e => new Exception(e.message)))
            .flatMap { token =>
              IO.fromTry(
                pdi.jwt.JwtUpickle
                  .decode(
                    token.jwt.raw,
                    options = JwtOptions(signature = false)
                  )
              )
            }
      yield expect(decoded.issuer.contains("io:twotm8:token"))
    }

    group("failiure"):
      integrationTest("unregistered user") { probe =>
        for
          nickname <- probe.generator.str(Nickname, 8 to 15)
          password <- probe.generator.string(8 to 15).map(Password(_))

          result <- probe.execute(
            endpoints.login,
            api.Payload.Login(nickname, password)
          )
        yield expect(
          result == Left(ErrorInfo.BadRequest("Invalid credentials"))
        )
      }

      integrationTest("wrong password") { probe =>
        for
          nickname <- probe.generator.str(Nickname, 8 to 15)
          password <- probe.generator.string(8 to 15).map(Password(_))
          wrongPassword = Password(password.process(_ + "!"))

          result <-
            probe.execute(
              endpoints.register,
              api.Payload.Register(nickname, password)
            ) *>
              probe.execute(
                endpoints.login,
                api.Payload.Login(nickname, wrongPassword)
              )
        yield expect(
          result == Left(ErrorInfo.BadRequest("Invalid credentials"))
        )
      }
  }
end Auth
