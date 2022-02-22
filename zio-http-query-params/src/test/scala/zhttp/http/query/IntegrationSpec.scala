package zhttp.http.query

import zhttp.http.Method.GET
import zhttp.http._
import zhttp.http.query.QueryParams.QueryParametersWrapper
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.duration.durationInt
import zio.schema.{DeriveSchema, Schema}
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test.assertM
import zio.{UIO, ZIO}

object IntegrationSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  sealed trait Sorting
  case object Ascending  extends Sorting
  case object Descending extends Sorting

  case class UserQuery(fields: List[String], perPage: Option[Int], page: Option[Int], sort: Sorting)
  implicit val schema: Schema[UserQuery] = DeriveSchema.gen[UserQuery]

  private def responseFromUserQuery(q: UserQuery): UIO[Response] =
    UIO(Response.apply(status = Status.OK, headers = Headers.empty, data = HttpData.fromString(q.toString)))

  private val queryApp = Http.collectZIO[Request] { case req @ GET -> !! =>
    ZIO.fromEither(req.queryParams.decode[UserQuery]).flatMap(responseFromUserQuery).mapError(err => new Exception(err))
  }

  private val app = serve(queryApp)

  private val clientRequestQueryParams = QueryParameters(
    Map(
      "fields"  -> List("a", "b", "c"),
      "perPage" -> List("1"),
      "page"    -> List("1"),
      "sort"    -> List("Ascending"),
    ),
  )

  def queryParamsSpec = suite("QueryParamsSpec") {
    suite("success") {
      testM("status is 200") {
        val res = queryApp.deploy(clientRequestQueryParams).status.run()
        assertM(res)(equalTo(Status.OK))
      }
    }
  }

  override def spec = suiteM("QueryParams") {
    app.as(List(queryParamsSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)
}
