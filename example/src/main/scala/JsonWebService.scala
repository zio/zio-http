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
    case class Create(user: User, ref: Ref[List[User]]) extends UserRequest
    case class Show(ref: Ref[List[User]])               extends UserRequest
    case object BadRequest                              extends UserRequest
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
  val userApp: Http[Any, Nothing, UserRequest, UserResponse] =
    Http.collectM[UserRequest]({
      case UserRequest.Show(ref)         => {
        for {
          users <- ref.get
        } yield UserResponse.Show(users)
      }
      case UserRequest.Create(user, ref) => {
        for {
          users <- ref.get
          _     <- ref.set(users :+ user)
        } yield UserResponse.Create(user)
      }
    })

  // Create HTTP route
  def app(ref: Ref[List[User]]): Http[Any, Nothing, Request, UResponse] = userApp
    .contramap[Request] {
      case Method.GET -> Root / "show"          => UserRequest.Show(ref)
      case req @ Method.POST -> Root / "create" => {
        UserRequest.Create(getUser(req.getBodyAsString.get), ref)
      }
      case _                                    => UserRequest.BadRequest
    }
    .map {
      case UserResponse.Show(users)  => Response.text(users.asJson.noSpaces)
      case UserResponse.Create(user) => Response.text(user.asJson.noSpaces)
    }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val ref: UIO[Ref[List[User]]] = Ref.make(List[User]())
    for {
      r <- ref
      s <- Server.start(8090, HttpApp(app(r))).exitCode
    } yield s
  }

}
