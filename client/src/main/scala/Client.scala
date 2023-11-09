package twotm8.client

import twotm8.endpoints
import twotm8.*, api.*

import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.model.Uri
import sttp.client3.SttpBackend
import scala.util.control.NonFatal

trait Client:
  def login(nickname: Nickname, password: Password): Either[ErrorInfo, Token]
  def wall(token: JWT): Either[ErrorInfo, Vector[Twot]]
  def me(token: JWT): Either[ErrorInfo, ThoughtLeader]
  def create_twot(text: String, token: JWT): Either[ErrorInfo, Unit]
end Client

object Client:
  def create(uri: String): Client =
    new ClientImpl(
      backend = CurlBackend(),
      interp = SttpClientInterpreter(),
      base = Uri.unsafeParse(uri)
    )

  private class ClientImpl(
      backend: SttpBackend[sttp.client3.Identity, Any],
      interp: SttpClientInterpreter,
      base: Uri
  ) extends Client:

    override def me(token: JWT): Either[ErrorInfo, ThoughtLeader] =
      interp
        .toSecureClientThrowDecodeFailures(
          endpoints.get_me,
          Some(base),
          backend
        )
        .apply(token)(())

    override def create_twot(
        text: String,
        token: JWT
    ): Either[ErrorInfo, Unit] =
      interp
        .toSecureClientThrowDecodeFailures(
          endpoints.create_twot,
          Some(base),
          backend
        )
        .apply(token)
        .apply(Payload.Create(Text(text)))

    override def login(nickname: Nickname, password: Password) =
      interp
        .toClientThrowDecodeFailures(endpoints.login, Some(base), backend)
        .apply(Payload.Login(nickname, password))

    override def wall(token: JWT) =
      interp
        .toSecureClientThrowDecodeFailures(
          endpoints.get_wall,
          Some(base),
          backend
        )
        .apply(token)
        .apply(())
  end ClientImpl
end Client

@main def hello =
  println(
    Client.create("https://twotm8.com").login(Nickname("wut"), Password("yut"))
  )
