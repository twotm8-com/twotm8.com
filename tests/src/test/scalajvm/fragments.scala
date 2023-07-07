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
import pdi.jwt.JwtClaim

import cats.syntax.all.*

trait Fragments:
  self: BaseTest =>
  def getAuthToken(probe: Probe): IO[Token] =
    for
      nickname <- probe.generator.str(Nickname, 8 to 15)
      password <- probe.generator.string(8 to 15).map(Password(_))
      token <-
        probe.execute(
          endpoints.register,
          api.Payload.Register(nickname, password)
        ) *>
          probe
            .execute(
              endpoints.login,
              api.Payload.Login(nickname, password)
            )
            .raise

      decoded <-
        IO.fromTry(
          pdi.jwt.JwtUpickle
            .decode(
              token.jwt.raw,
              options = JwtOptions(signature = false)
            )
        )
    yield token
end Fragments
