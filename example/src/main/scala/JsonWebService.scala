import io.circe.generic.auto._
import io.circe.syntax._
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.random.Random
import zio.test.magnolia._
import zio.test.{Gen, Sized}

/**
 * Example to build app using JSON web service
 */
object JsonWebService extends App {

  sealed trait UserRequest
  object UserRequest  {
    case class Post(req: Request, content: Option[String]) extends UserRequest
    case class Get(req: Request)                           extends UserRequest
    case object BadRequest                                 extends UserRequest
  }
  sealed trait UserResponse
  object UserResponse {
    case class StatusResponse(msg: String) extends UserResponse
    case class JsonResponse(data: String)  extends UserResponse
  }

  final case class Employee(id: Long, name: String, experience: Int)

  val employees: Gen[Random with Sized, Employee] = DeriveGen[Employee]

  //get employees
  def getDetails(): ZIO[Random with Sized, Nothing, String] = for {
    a <- employees.runCollectN(10)
  } yield a.asJson.noSpaces

  val user: Http[Random with Sized, Nothing, UserRequest, UserResponse] =
    Http.collectM[UserRequest]({
      case UserRequest.Get(Method.GET -> Root / "employees")             =>
        getDetails().map(x => UserResponse.JsonResponse(x))
      case UserRequest.Post(Method.POST -> Root / "createUser", content) =>
        UIO(UserResponse.StatusResponse(content match {
          case Some(content) if content.nonEmpty => s"user created with content: $content"
          case None                              => "failed to create user"
          case _                                 => "failed to create user"
        }))
    })

  // Create HTTP route
  val app: Http[Random with Sized, Nothing, Request, UResponse] = user
    .contramap[Request] {
      case req @ Method.GET -> Root / "employees"   => UserRequest.Get(req)
      case req @ Method.POST -> Root / "createUser" => UserRequest.Post(req, req.getBodyAsString)
      case _                                        => UserRequest.BadRequest
    }
    .map {
      case UserResponse.StatusResponse(msg) => Response.text(msg)
      case UserResponse.JsonResponse(data)  => Response.jsonString(data)
    }

  // Run it like any simple app
  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    Server.start(8090, app.silent).provideCustomLayer(Sized.live(10)).exitCode

}
