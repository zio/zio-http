import zhttp.http._
import zhttp.service.Server
import zio._

object ConcreteEntity extends App {

  case class CreateUser(name: String)
  case class UserCreated(id: Long)

  val user: Http[Any, Nothing, CreateUser, UserCreated] =
    Http.collect[CreateUser]({ case CreateUser(_) =>
      UserCreated(2)
    })

  val app: Http[Any, Nothing, Request, Response[Any, Nothing]] = user
    .contramap[Request](req => CreateUser(req.endpoint._2.toString)) //Http[Any, Nothing, Request, UserCreated]
    .map(userCreated => Response.text(userCreated.id.toString))      //Http[Any, Nothing, Request, Response]

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, HttpApp(app)).exitCode
}
