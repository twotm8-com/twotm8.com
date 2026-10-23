package twotm8

import snunit.config.*

import scala.concurrent.duration.*

/** The FreeUnit configuration of the executable: the API paths go to the application, everything else is the static
  * frontend (with `index.html` as the fallback of the single page application).
  */
def unitConfig: UnitConfig =
  val static = sys.env.getOrElse("STATIC_DIR", "/www/static")
  UnitConfig(
    listeners = Seq(Listener(address = s"*:${sys.env.getOrElse("PORT", "8080")}", pass = Pass.Route("main"))),
    routes = Map(
      "main" -> Seq(
        RouteStep(
          Action.PassTo(Pass.App),
          Match(uri = Seq("/api/*"))
        ),
        RouteStep(
          Action.Share(Seq(s"$static$$uri")),
          Match(uri = Seq("~^((/(.*)\\.(js|css|html))|/)$"))
        ),
        RouteStep(Action.Share(Seq(s"$static/index.html")))
      )
    ),
    application = Application(
      processes = Some(Processes.Dynamic(max = 50, spare = 2, idleTimeout = Some(180.seconds))),
      limits = Some(Limits(requests = Some(1000), timeout = Some(1.second)))
    )
  )
