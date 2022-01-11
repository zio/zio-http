package example

import zhttp.http.Middleware.codec
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.json._

object Codec extends App {

  // Input
  case class CreateUser(name: String)

  // Output
  case class UserCreated(id: Long, name: String)
  object UserCreated {
    implicit val decoder: JsonDecoder[UserCreated] = DeriveJsonDecoder.gen[UserCreated]
    implicit val encoder: JsonEncoder[UserCreated] = DeriveJsonEncoder.gen[UserCreated]
  }

  val user: Http[Any, Nothing, CreateUser, UserCreated]                         =
    Http.collect[CreateUser] { case CreateUser(name) =>
      UserCreated(2, name)
    }
  // Middleware to transform above http to HttpApp
  val mid: Middleware[Any, Nothing, CreateUser, UserCreated, Request, Response] =
    codec[Request, UserCreated](
      decoder = req => CreateUser(req.path.asString.filterNot(_ == '/')),
      encoder = userCreated => Response.json(userCreated.toJson),
    )
  // Create HTTP route
  val app: HttpApp[Any, Nothing]                                                = user @@ mid

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
