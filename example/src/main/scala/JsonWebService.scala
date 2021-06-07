import io.circe.Encoder
import io.circe.syntax.EncoderOps
import zhttp.http._
import zhttp.service.Server
import zio._

/**
 * Example to build app using JSON web service
 */
object JsonWebService extends App {

  sealed trait UserRequest
  object UserRequest  {
    case class Post(route: Route, content: Option[String]) extends UserRequest
    case class Get(route: Route)                           extends UserRequest
    case object BadRequest                                 extends UserRequest
  }
  sealed trait UserResponse
  object UserResponse {
    case class StatusResponse(msg: String) extends UserResponse
    case class JsonResponse(data: String)  extends UserResponse
  }

  final case class Employee(id: Long, name: String, experience: Int)

  implicit val employeeEncoder: Encoder[Employee] =
    Encoder.forProduct3("id", "name", "experience")(emp => (emp.id, emp.name, emp.experience))

  val employees = List(Employee(1, "abc", 3), Employee(2, "def", 2), Employee(3, "xyz", 4))

  //get employee details if employee exists
  def getDetails(id: String): String = employees.filter(_.id.toString.equals(id)).asJson.noSpaces

  val user: Http[Any, Nothing, UserRequest, UserResponse] =
    Http.collect[UserRequest]({
      case UserRequest.Get((Method.GET, Root / "employee" / id))         => UserResponse.JsonResponse(getDetails(id))
      case UserRequest.Post((Method.POST, Root / "createUser"), content) =>
        UserResponse.StatusResponse(content match {
          case Some(content) if !content.isEmpty => s"user created with content: ${content}"
          case None                              => "failed to create user"
          case _                                 => "failed to create user"
        })
    })

  // Create HTTP route
  val app: Http[Any, Nothing, Request, UResponse] = user
    .contramap[Request](req =>
      req match {
        case Method.GET -> Root / "employee" / _ => UserRequest.Get(req.route)
        case Method.POST -> Root / "createUser"  => UserRequest.Post(req.route, req.getBodyAsString)
        case _                                   => UserRequest.BadRequest
      },
    )
    .map(response =>
      response match {
        case UserResponse.StatusResponse(msg) => Response.text(msg)
        case UserResponse.JsonResponse(data)  => Response.jsonString(data)
      },
    )

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
