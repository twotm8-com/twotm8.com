package twotm8
package frontend

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import upickle.default.ReadWriter

sealed trait Page derives ReadWriter
object Page:
  case object Wall extends Page
  case object Login extends Page
  case object Logout extends Page
  case object Register extends Page
  case class Profile(authorId: String) extends Page

  val mainRoute = Route.static(Page.Wall, root / endOfSegments)
  val loginRoute = Route.static(Page.Login, root / "login")
  val logoutRoute = Route.static(Page.Logout, root / "logout")
  val registerRoute =
    Route.static(Page.Register, root / "register")

  val profileRoute = Route(
    encode = (stp: Profile) => stp.authorId,
    decode = (arg: String) => Profile(arg),
    pattern = root / "thought_leaders" / segment[String] / endOfSegments
  )

  val router = new Router[Page](
    routes =
      List(mainRoute, loginRoute, registerRoute, profileRoute, logoutRoute),
    getPageTitle = {
      case Wall       => "T8: safe space for thought leaders"
      case Login      => "T8: login"
      case Logout     => "T8: logout"
      case Register   => "T8: register"
      case Profile(a) => s"T8: $a"
    },
    serializePage = pg => upickle.default.writeJs(pg).render(),
    deserializePage = str => upickle.default.read[Page](str)
  )(
    popStateEvents = windowEvents(_.onPopState),
    owner = L.unsafeWindowOwner
  )
end Page

def navigateTo(page: Page)(using router: Router[Page]): Binder[HtmlElement] =
  Binder { el =>
    import org.scalajs.dom

    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]

    if isLinkElement then el.amend(href(router.absoluteUrlForPage(page)))

    (onClick
      .filter(ev =>
        !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey))
      )
      .preventDefault
      --> (_ => redirectTo(page))).bind(el)
  }
