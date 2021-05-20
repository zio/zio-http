import zhttp.http._
import zhttp.service.Server
import zio._

object ConcreteEntity extends App {

  case class CreateUser(name: String)
  case class UserCreated(id: Long)

  object CreateUser {
    def fromRequest(req: Request): CreateUser = CreateUser(req.endpoint._2.toString)
  }

  object UserCreated {
    def asResponse(userCreated: UserCreated): Response[Any, Nothing] = Response.text(userCreated.id.toString)
  }

  val user: Http[Any, Nothing, CreateUser, UserCreated] =
    Http.collect[CreateUser]({ case CreateUser(_) =>
      UserCreated(2)
    })

  val app: Http[Any, Nothing, Request, Response[Any, Nothing]] = user
    .contramap(req => CreateUser.fromRequest(req))           //Http[Any, Nothing, Request, UserCreated]
    .map(userCreated => UserCreated.asResponse(userCreated)) //Http[Any, Nothing, Request, Response]

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
