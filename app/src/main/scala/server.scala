package twotm8

import roach.Database
import scribe.Level
import scribe.format.Formatter
import scribe.handler.LogHandler
import scribe.writer.SystemErrWriter
import snunit.*
import snunit.tapir.SNUnitIdServerInterpreter.*
import snunit.tapir.*
import twotm8.db.DB

import scala.concurrent.duration.*
import scala.scalanative.unsafe.Zone
import scala.util.Using
import roach.Pool
import roach.Migrate

def connection_string() =
  sys.env.getOrElse(
    "DATABASE_URL", {
      val host = sys.env.getOrElse("PG_HOST", "localhost")
      val port = sys.env.getOrElse("PG_PORT", "5432")
      val password = sys.env.getOrElse("PG_PASSWORD", "mysecretpassword")
      val user = sys.env.getOrElse("PG_USER", "postgres")
      val db = sys.env.getOrElse("PG_DB", "postgres")

      s"postgresql://$user:$password@$host:$port/$db"
    }
  )

object Main:
  def main(args: Array[String]): Unit =
    val postgres = connection_string()

    scribe.Logger.root
      .clearHandlers()
      .withHandler(
        writer = scribe.writer.SystemErrWriter,
        outputFormat = scribe.output.format.ANSIOutputFormat
      )
      .withMinimumLevel(Level.Debug)
      .replace()

    Zone { implicit z =>
      Pool.single(postgres) { pool =>
        given Settings = Settings(
          tokenExpiration = 14.days,
          secretKey = Secret(
            sys.env.getOrElse[String](
              "JWT_SECRET",
              throw new Exception("Missing token configuration")
            )
          )
        )

        Migrate.all(pool)(roach.ResourceFile("/V00001.Schema.sql"))

        val app = App(DB.postgres(pool))
        val routes = api.Api(app).routes

        SyncServerBuilder
          .setRequestHandler(toHandler(routes))
          .build()
          .listen()
      }
    }
  end main
end Main
