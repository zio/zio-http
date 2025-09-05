//> using dep "dev.zio::zio-http:3.4.1"

package example.middleware

import zio.Config.Secret
import zio._

import zio.http._
import zio.http.codec.PathCodec.string

object CustomAuthProviding extends ZIOAppDefault {

  final case class AuthContext(value: String)

  // Provides an AuthContext to the request handler
  val provideContext: HandlerAspect[Any, AuthContext] = HandlerAspect.customAuthProviding[AuthContext] { r =>
    {
      r.headers.get(Header.Authorization).flatMap {
        case Header.Authorization.Basic(uname, password) if Secret(uname.reverse) == password =>
          Some(AuthContext(uname))
        case _                                                                                =>
          None
      }

    }
  }

  // Multiple routes that require an AuthContext via withContext
  val secureRoutes: Routes[AuthContext, Response] = Routes(
    Method.GET / "a" -> handler((_: Request) => withContext((ctx: AuthContext) => Response.text(ctx.value))),
    Method.GET / "b" / int("id")      -> handler((id: Int, _: Request) =>
      withContext((ctx: AuthContext) => Response.text(s"for id: $id: ${ctx.value}")),
    ),
    Method.GET / "c" / string("name") -> handler((name: String, _: Request) =>
      withContext((ctx: AuthContext) => Response.text(s"for name: $name: ${ctx.value}")),
    ),
  )

  val app: Routes[Any, Response] = secureRoutes @@ provideContext

  val run = Server.serve(app).provide(Server.default)

}
