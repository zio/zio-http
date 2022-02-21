package example

import zhttp.http.Method.GET
import zhttp.http._
import zhttp.http.query.QueryParams._
import zhttp.service.Server
import zio._
import zio.schema.{DeriveSchema, Schema}

object HelloWorldWithQuery extends App {

  sealed trait Sorting
  case object Ascending  extends Sorting
  case object Descending extends Sorting

  // case class UserQuery(fields: List[String], perPage: Int, page: Int, sort: Sorting)
  // case class UserQuery(fields: List[String], perPage: Int, page: Int, sort: String)
  case class UserQuery(fields: String, perPage: Int, page: Int, sort: String)
  implicit val schema: Schema[UserQuery] = DeriveSchema.gen[UserQuery]

  def responseFromUserQuery(q: UserQuery): UIO[Response] =
    UIO(Response.apply(status = Status.OK, headers = Headers.empty, data = HttpData.fromString(q.toString)))

  val app = Http.collectZIO[Request] { case req @ GET -> !! / "users" / "list" =>
    ZIO.fromEither(req.queryParams.decode[UserQuery]).flatMap(responseFromUserQuery).mapError(err => new Exception(err))
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode

}
