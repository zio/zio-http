import zhttp.http._
import zhttp.service.Server
import zio._

/**
 * Example to build app on concrete entity
 */
object ConcreteEntity extends App {
  //Request
  case class CreateUser(name: String)

  //Response
  case class UserCreated(id: Long)

  val user: Http[Any, Nothing, CreateUser, UserCreated] =
    Http.collect[CreateUser] { case CreateUser(_) =>
      UserCreated(2)
    }

  val app: Http[Any, Nothing, Request, Response[Any, Nothing]] = user
    .contramap[Request](req => CreateUser(req.url.toString))    //Http[Any, Nothing, Request, UserCreated]
    .map(userCreated => Response.text(userCreated.id.toString)) //Http[Any, Nothing, Request, Response]

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
