//> using dep "dev.zio::zio-http:3.4.0"

package example.middleware

import zio.Config.Secret
import zio._

import zio.blocks.context.IsNominalType
import zio.http._
import zio.http.ResultType._
import zio.http.codec.PathCodec.string
import zio.http.netty.server.NettyServer

object CustomAuthProviding extends ZIOAppDefault {

  final case class AuthContext(value: String)
  implicit val authContextIsNominal: IsNominalType[AuthContext] = IsNominalType.derived[AuthContext]

  // Provides an AuthContext to the request handler
  val provideContext: Middleware[Any, AuthContext] = Middleware.customAuth[AuthContext] { r =>
    r.headers.get(Header.Authorization) match {
      case Some(Header.Authorization.Basic(uname, password)) if Secret(uname.reverse) == password =>
        valueAsOutcome(AuthContext(uname))
      case _                                                                                      =>
        haltAsOutcome(Halt(Response.unauthorized))
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

  val run = Server.serve(app).provide(NettyServer.default)

}
