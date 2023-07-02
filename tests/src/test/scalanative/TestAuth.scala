package twotm8

import java.util.UUID
import scala.scalanative.unsafe.*
import openssl.OpenSSL

object TestAuth extends weaver.FunSuite:
  test("SHA256 encryption is solid") {
    val knownHash =
      "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
    val knownPassword = "password"

    Zone { implicit z =>
      assert(OpenSSL.sha256(knownPassword) == knownHash)
    }
  }
  test("JWT: same settings produce same tokens") {
    import scala.concurrent.duration.*
    val settings1 = Settings(20.seconds, Secret("hello1"))
    val id = AuthorId(UUID.randomUUID())

    Zone { implicit z =>

      val token1 = Auth.token(id)(using z)(using settings1)
      val token2 = Auth.token(id)(using z)(using settings1)

      assert(token1.jwt == token2.jwt)
      assert(token1.expiresIn == token2.expiresIn)
    }

  }

  test("JWT: different settings produce different tokens") {
    import scala.concurrent.duration.*
    val settings1 = Settings(20.seconds, Secret("hello1"))
    val settings2 = Settings(40.seconds, Secret("hello2"))
    val id = AuthorId(UUID.randomUUID())

    Zone { implicit z =>

      val token1 = Auth.token(id)(using z)(using settings1)
      val token2 = Auth.token(id)(using z)(using settings2)

      assert(token1.jwt != token2.jwt)
      assert(token1.expiresIn != token2.expiresIn)
    }

  }

  test("JWT: expired tokens no longer validate") {
    import scala.concurrent.duration.*
    given Settings = Settings(1.seconds, Secret("hello1"))
    val id = AuthorId(UUID.randomUUID())

    Zone { implicit z =>

      val token1 = Auth.token(id)
      def isValid = Auth.validate(token1.jwt).isDefined

      assert(isValid)
      Thread.sleep(1000) // 3 sec for posterity
      assert(!isValid)
    }
  }

  test("JWT: token can be validated if settings match") {
    import scala.concurrent.duration.*
    val settings1 = Settings(20.seconds, Secret("hello1"))
    val settings2 = Settings(40.seconds, Secret("hello2"))
    val id = AuthorId(UUID.randomUUID())

    Zone { implicit z =>

      val token1 = Auth.token(id)(using z)(using settings1)
      val token2 = Auth.token(id)(using z)(using settings2)

      {
        given Settings = settings1
        val validated = Auth.validate(token1.jwt)
        assert(validated.isDefined)
      }

      {
        given Settings = settings2
        val validated = Auth.validate(token1.jwt)
        assert(validated.isEmpty)
      }

      {
        given Settings = settings2
        val validated = Auth.validate(token2.jwt)
        assert(validated.isDefined)
      }

      {
        given Settings = settings1
        val validated = Auth.validate(token2.jwt)
        assert(validated.isEmpty)
      }

    }
  }
end TestAuth
