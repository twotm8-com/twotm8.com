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

object TwotsTest extends Twots(url = None)
class Twots(url: Option[String] = None) extends BaseTest(url) with Fragments:
  group("posting"):
    integrationTest("user's twots appear on user's wall"): probe =>
      for
        token <- getAuthToken(probe).map(_.jwt)

        _ <- Vector("howdy!", "dowdy").map(Text(_)).traverse { text =>
          probe
            .executeSecure(
              endpoints.create_twot,
              api.Payload.Create(text),
              token
            )
            .raise
        }

        twots <- probe.executeSecure(endpoints.get_wall, (), token).raise
      // TODO: this is stupid, stop uppercasing everything
      yield expect(twots.map(_.content) == Vector("DOWDY", "HOWDY!"))

    integrationTest("user's followers and guests can see user's twots"):
      probe =>
        for
          userToken <- getAuthToken(probe).map(_.jwt)

          me <- probe
            .executeSecure(endpoints.get_me, (), userToken)
            .raise

          userId = me.id
          userNickname = me.nickname

          _ <- Vector("howdy!", "dowdy").map(Text(_)).traverse { text =>
            probe
              .executeSecure(
                endpoints.create_twot,
                api.Payload.Create(text),
                userToken
              )
              .raise
          }

          followerToken <- getAuthToken(probe).map(_.jwt)

          _ <- probe.executeSecure(
            endpoints.add_follower,
            api.Payload.Follow(userId),
            followerToken
          )

          guestTwots <- probe
            .executeSecure(
              endpoints.get_thought_leader,
              me.nickname.raw,
              None
            )
            .raise
            .map(_.twots)

          authTwots <- probe
            .executeSecure(
              endpoints.get_thought_leader,
              me.nickname.raw,
              Some(followerToken)
            )
            .raise
            .map(_.twots)
        // TODO: this is stupid, stop uppercasing everything
        yield expect(guestTwots.map(_.content) == Vector("DOWDY", "HOWDY!")) and
          expect(authTwots.map(_.content) == Vector("DOWDY", "HOWDY!"))

end Twots
