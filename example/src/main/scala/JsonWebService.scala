import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{HCursor, Json}
import zhttp.http._
import zhttp.service.Server
import zio._

/**
 * Example to build app using JSON web service
 */
object JsonWebService extends App {

  sealed trait UserRequest
  object UserRequest  {
    case class Create(user: User) extends UserRequest
    case object Show              extends UserRequest
    case object BadRequest        extends UserRequest
  }
  sealed trait UserResponse
  object UserResponse {
    case class Show(users: List[User]) extends UserResponse
    case class Create(user: User)      extends UserResponse
  }

  final case class User(id: Long, name: String)

  def getUser(jsonString: String): User = {
    val json: Json      = parse(jsonString).getOrElse(null)
    val cursor: HCursor = json.hcursor
    val id              = cursor.downField("id").as[Long]
    val name            = cursor.downField("name").as[String]
    User(id.getOrElse(0), name.getOrElse(""))
  }
  var users                           = List.empty[User]
  def addUser(user: User): List[User] = users :+ user

  val userApp: Http[Any, Nothing, UserRequest, UserResponse] =
    Http.collectM[UserRequest]({
      case UserRequest.Show         => UIO(UserResponse.Show(users))
      case UserRequest.Create(user) => {
        users = addUser(user)
        UIO(UserResponse.Create(user))
      }
    })

  // Create HTTP route
  val app: Http[Any, Nothing, Request, UResponse] = userApp
    .contramap[Request] {
      case Method.GET -> Root / "show"              => UserRequest.Show
      case req @ Method.POST -> Root / "createUser" => {
        UserRequest.Create(getUser(req.getBodyAsString.get))
      }
      case _                                        => UserRequest.BadRequest
    }
    .map {
      case UserResponse.Show(users)  => Response.text(users.asJson.noSpaces)
      case UserResponse.Create(user) => Response.text(user.asJson.noSpaces)
    }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode

}
