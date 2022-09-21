package zio.benchmarks

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.openjdk.jmh.annotations._
import zio.http._
import zio.http.api._
import zio.http.model.Method
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.{Scope => _, _}
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import org.http4s.HttpRoutes
import scala.concurrent.{Await, Future}
import zio.interop.catz._
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.stream.ActorMaterializer
import org.http4s.{Request => Request4s}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.ExecutionContext.Implicits.global
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.tapir.server.ServerEndpoint.Full

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ApiBenchmark {
  implicit val system = ActorSystem("api-benchmark-actor-system")

  private val REPEAT_N = 1000

  // # Small Data Request

  // API DSL
  val usersPosts =
    API
      .get(In.literal("users") / In.int / In.literal("posts") / In.int)
      .in(In.query("query"))
      .out[ExampleData]

  val handledUsersPosts =
    usersPosts.handle { case (userId, postId, limit) =>
      ZIO.succeed(ExampleData(userId, postId, limit))
    }

  val apiHttpApp = handledUsersPosts.toHttpApp

  // Collect DSL
  val collectHttpApp = Http.collectZIO[Request] { //
    case req @ Method.GET -> !! / "users" / userId / "posts" / postId =>
      val userIdInt = userId.toInt
      val postIdInt = postId.toInt
      val query     = req.url.queryParams.get("query").flatMap(_.headOption).get
      ZIO.succeed(
        Response.json(
          ExampleData(userIdInt, postIdInt, query).toJson,
        ),
      )
  }

  // Tapir Akka DSL

  val usersPostsEndpoint: Endpoint[Unit, (Int, Int, String), Unit, ExampleData, Any] =
    endpoint
      .in("users" / path[Int] / "posts" / path[Int])
      .in(query[String]("query"))
      .out(jsonBody[ExampleData])

  private val handledUsersPostsEndpoint =
    usersPostsEndpoint.serverLogic[Future] { case (userId, postId, query) =>
      Future.successful(Right(ExampleData(userId, postId, query)))
    }

  val usersPostsRoute: Route =
    AkkaHttpServerInterpreter().toRoute(handledUsersPostsEndpoint)

  val routeFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(usersPostsRoute)

  // Tapir Http4s DSL

  val handledUsersPostsEndpointHttp4s =
    usersPostsEndpoint.serverLogic[Task] { case (userId, postId, query) =>
      ZIO.succeed(Right(ExampleData(userId, postId, query)))
    }

  val usersPostsHttp4sRoute: HttpRoutes[Task] =
    Http4sServerInterpreter[Task]().toRoutes(handledUsersPostsEndpointHttp4s)

  val (smallDataRequest, smallDataRequestAkka, smallDataRequestHttp4s) = requestsFromString(
    "http://localhost:8080/users/1/posts/2?query=cool",
  )

  @Benchmark
  def benchmarkSmallDataZioApi(): Unit =
    unsafeRun {
      apiHttpApp(smallDataRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkSmallDataZioCollect(): Unit =
    unsafeRun {
      collectHttpApp(smallDataRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkSmallDataTapirAkka(): Unit =
    unsafeRun {
      ZIO.fromFuture(_ => repeatNFuture(REPEAT_N)(routeFunction(smallDataRequestAkka)))
    }

  @Benchmark
  def benchmarkSmallDataTapirHttp4s(): Unit =
    unsafeRun {
      usersPostsHttp4sRoute(smallDataRequestHttp4s).value.repeatN(REPEAT_N)
    }

  // # Deep Path

  // API DSL

  val deepPathHttpApp = API
    .get(
      In.literal("first") /
        In.int / "second" / In.int / "third" / In.int / "fourth" / In.int / "fifth" / In.int / "sixth" / In.int / "seventh" / In.int,
    )
    .handle { _ =>
      ZIO.unit
    }
    .toHttpApp

  // Collect DSL

  val deepPathCollectHttpApp = Http.collectZIO[Request] { //
    case Method.GET -> !! / "first" / id1 / "second" / id2 / "third" / id3 / "fourth" / id4 / "fifth" / id5 / "sixth" / id6 / "seventh" / id7 =>
      val _ = id1.toInt
      val _ = id2.toInt
      val _ = id3.toInt
      val _ = id4.toInt
      val _ = id5.toInt
      val _ = id6.toInt
      val _ = id7.toInt
      ZIO.unit
  }

  // Tapir Akka DSL

  val deepPathEndpoint: Endpoint[Unit, (Int, Int, Int, Int, Int, Int, Int), Unit, Unit, Any] =
    endpoint
      .in(
        "first" / path[Int] / "second" / path[Int] / "third" / path[Int] / "fourth" / path[Int] / "fifth" /
          path[Int] / "sixth" / path[Int] / "seventh" / path[Int],
      )

  private val handledDeepPathEndpoint =
    deepPathEndpoint.serverLogic[Future] { _ =>
      Future.successful(Right(()))
    }

  val deepPathRoute: Route =
    AkkaHttpServerInterpreter().toRoute(handledDeepPathEndpoint)

  val deepPathRouteFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(deepPathRoute)

  // Tapir Http4s DSL

  val handledDeepPathEndpointHttp4s =
    deepPathEndpoint.serverLogic[Task] { _ =>
      ZIO.succeed(Right(()))
    }

  val deepPathHttp4sRoute: HttpRoutes[Task] =
    Http4sServerInterpreter[Task]().toRoutes(handledDeepPathEndpointHttp4s)

  val deepPathRequest4s =
    Request4s[Task](uri =
      org.http4s.Uri.unsafeFromString(
        "http://localhost:8080/first/1/second/2/third/3/fourth/4/fifth/5/sixth/6/seventh/7",
      ),
    )

  val (deepPathRequest, deepPathRequestAkka, deepPathRequestHttp4s) = requestsFromString(
    "http://localhost:8080/first/1/second/2/third/3/fourth/4/fifth/5/sixth/6/seventh/7",
  )

  @Benchmark
  def benchmarkDeepPathZioApi(): Unit =
    unsafeRun {
      deepPathHttpApp(deepPathRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkDeepPathZioCollect(): Unit =
    unsafeRun {
      deepPathCollectHttpApp(deepPathRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkDeepPathTapirAkka(): Unit =
    unsafeRun {
      ZIO.fromFuture(_ => repeatNFuture(REPEAT_N)(deepPathRouteFunction(deepPathRequestAkka)))
    }

  @Benchmark
  def benchmarkDeepPathTapirHttp4s(): Unit =
    unsafeRun {
      deepPathHttp4sRoute(deepPathRequestHttp4s).value.repeatN(REPEAT_N)
    }

  // # Broad Path

  // users / int / posts / int / comments / int
  // users / int / posts / int / comments
  // users / int / posts / int
  // users / int / posts
  // users / int
  // users
  // posts / int / comments / int
  // posts / int / comments
  // posts / int
  // posts
  // comments / int
  // comments

  // API DSL

  val broadUsers                = API.get(In.literal("users")).handle { _ => ZIO.unit }
  val broadUsersId              = API.get(In.literal("users") / In.int).handle { _ => ZIO.unit }
  val boardUsersPosts           =
    API.get(In.literal("users") / In.int / In.literal("posts")).handle { _ => ZIO.unit }
  val boardUsersPostsId         =
    API.get(In.literal("users") / In.int / In.literal("posts") / In.int).handle { _ => ZIO.unit }
  val boardUsersPostsComments   =
    API.get(In.literal("users") / In.int / In.literal("posts") / In.int / In.literal("comments")).handle { _ =>
      ZIO.unit
    }
  val boardUsersPostsCommentsId =
    API.get(In.literal("users") / In.int / In.literal("posts") / In.int / In.literal("comments") / In.int).handle { _ =>
      ZIO.unit
    }
  val broadPosts                = API.get(In.literal("posts")).handle { _ => ZIO.unit }
  val broadPostsId              = API.get(In.literal("posts") / In.int).handle { _ => ZIO.unit }
  val boardPostsComments        =
    API.get(In.literal("posts") / In.int / In.literal("comments")).handle { _ => ZIO.unit }
  val boardPostsCommentsId      =
    API.get(In.literal("posts") / In.int / In.literal("comments") / In.int).handle { _ => ZIO.unit }
  val broadComments             = API.get(In.literal("comments")).handle { _ => ZIO.unit }
  val broadCommentsId           = API.get(In.literal("comments") / In.int).handle { _ => ZIO.unit }

  val broadPathHttpApp =
    (
      broadUsers ++
        broadUsersId ++
        boardUsersPosts ++
        boardUsersPostsId ++
        boardUsersPostsComments ++
        boardUsersPostsCommentsId ++
        broadPosts ++
        broadPostsId ++
        boardPostsComments ++
        boardPostsCommentsId ++
        broadComments ++
        broadCommentsId
    ).toHttpApp

  // Collect DSL

  val broadPathCollectHttpApp = Http.collectZIO[Request] { //
    case Method.GET -> !! / "users" / userId / "posts" / postId / "comments" / commentId =>
      val _ = userId.toInt
      val _ = postId.toInt
      val _ = commentId.toInt
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts" / postId / "comments"             =>
      val _ = userId.toInt
      val _ = postId.toInt
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts" / postId                          =>
      val _ = userId.toInt
      val _ = postId.toInt
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts"                                   =>
      val _ = userId.toInt
      ZIO.unit
    case Method.GET -> !! / "users" / userId                                             =>
      val _ = userId.toInt
      ZIO.unit
    case Method.GET -> !! / "users"                                                      =>
      ZIO.unit
    case Method.GET -> !! / "posts" / postId / "comments" / commentId                    =>
      val _ = postId.toInt
      val _ = commentId.toInt
      ZIO.unit
    case Method.GET -> !! / "posts" / postId / "comments"                                =>
      val _ = postId.toInt
      ZIO.unit
    case Method.GET -> !! / "posts" / postId                                             =>
      val _ = postId.toInt
      ZIO.unit
    case Method.GET -> !! / "posts"                                                      =>
      ZIO.unit
    case Method.GET -> !! / "comments" / commentId                                       =>
      val _ = commentId.toInt
      ZIO.unit
    case Method.GET -> !! / "comments"                                                   =>
      ZIO.unit
  }

  // Tapir Akka DSL

  private def httpRequestFromString(url: String): Request =
    Request(url = URL.fromString(url).toOption.get)

  private def akkaHttpRequestFromString(url: String): HttpRequest =
    HttpRequest(uri = url)

  private def http4sRequestFromString(url: String): Request4s[Task] =
    Request4s[Task](uri = org.http4s.Uri.unsafeFromString(url))

  private def requestsFromString(url: String): (Request, HttpRequest, Request4s[Task]) =
    (httpRequestFromString(url), akkaHttpRequestFromString(url), http4sRequestFromString(url))

  private def unsafeRun[E, A](zio: ZIO[Any, E, A]): Unit = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(zio.unit)
      .getOrThrowFiberFailure()
  }

  private def repeatNFuture[A](n: Int)(f: => Future[A]): Future[A] =
    if (n == 0) f
    else f.flatMap(_ => repeatNFuture(n - 1)(f))
}

// Example Data Types

final case class ExampleData(userId: Int, postId: Int, query: String)

object ExampleData {
  implicit val schema: Schema[ExampleData] =
    DeriveSchema.gen[ExampleData]

  implicit val jsonCodec: JsonCodec[ExampleData] =
    DeriveJsonCodec.gen[ExampleData]

  implicit val encoder: Encoder[ExampleData] =
    deriveEncoder[ExampleData]

  implicit val decoder: Decoder[ExampleData] =
    deriveDecoder[ExampleData]

}
