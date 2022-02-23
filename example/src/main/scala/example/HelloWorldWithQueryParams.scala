package example

import zhttp.http.Method.GET
import zhttp.http._
import zhttp.macros.QueryParamsSupport._
import zhttp.service.Server
import zio._

object HelloWorldWithQueryParams extends App {

  sealed trait Sorting
  case object Ascending  extends Sorting
  case object Descending extends Sorting

  case class UserQuery(fields: List[String], perPage: Option[Int], page: Option[Int], sort: Sorting)

  def responseFromUserQuery(q: UserQuery): UIO[Response] =
    UIO(Response.apply(status = Status.OK, headers = Headers.empty, data = HttpData.fromString(q.toString)))

  val app = Http.collectZIO[Request] { case req @ GET -> !! / "users" / "list" =>
    ZIO
      .fromEither(decode[UserQuery](req.queryParams.raw))
      .flatMap(responseFromUserQuery)
      .mapError(err => new Exception(err))
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode

}
