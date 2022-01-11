package example

import zhttp.http.Middleware.codec
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.json._

object Codec extends App {

  case class CreateUser(name: String)            // Input
  case class UserCreated(id: Long, name: String) // Output
  implicit val userCodec: JsonCodec[UserCreated] = DeriveJsonCodec.gen[UserCreated]

  val user = Http.collect[CreateUser] { case CreateUser(name) => UserCreated(2, name) }

  // Middleware to transform above http to HttpApp

  val mid                        = codec[Request, UserCreated](
    _ => CreateUser("John"),
    user => Response.json(user.toJson),
  )
  // Create HTTP route
  val app: HttpApp[Any, Nothing] = user @@ mid

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
