package zio.benchmarks

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.{HttpRoutes, Request => Request4s}
import org.openjdk.jmh.annotations._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.{path => tpath}
import zio.http._
import zio.http.api._
import zio.http.model.Method
import zio.interop.catz._
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.{Scope => _, _}

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

// TODO: attempt the following optimizations
//
// BEFORE OPTIMIZATIONS
// [info] ApiBenchmark.benchmarkBroadZioApi           thrpt    4  667.506 ± 575.568  ops/s <-----
// [info] ApiBenchmark.benchmarkBroadZioCollect       thrpt    4  119.175 ±  30.864  ops/s
//
// [info] ApiBenchmark.benchmarkDeepPathZioApi        thrpt    4  433.606 ± 708.764  ops/s
// [info] ApiBenchmark.benchmarkDeepPathZioCollect    thrpt    4  855.360 ± 342.678  ops/s
//
// - Change HandlerTree to use a Map for literal routes
//
// [info] ApiBenchmark.benchmarkBroadZioApi         thrpt    2  923.077          ops/s
// [info] ApiBenchmark.benchmarkBroadZioCollect     thrpt    2  125.231          ops/s
//
// [info] ApiBenchmark.benchmarkDeepPathZioApi      thrpt    2  568.990          ops/s
// [info] ApiBenchmark.benchmarkDeepPathZioCollect  thrpt    2  900.059          ops/s
//
// POSSIBLE MICRO-OPTIMIZATIONS (X means it didn't make much of a difference)
// X Pre-compute the OptimizedAPIHandler (APIServer)
// X  Push handle method into lookup
// - Use array instead of appending to Chunk in HandleTree for result builder
// - Use `FromFunctionHExit` instead of Http.collectZIO

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ApiBenchmark {
  implicit val actorSystem: ActorSystem     = ActorSystem("api-benchmark-actor-system")
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  @TearDown
  def shutdown(): Unit = {
    println("\n\n Shutting down actor system \n\n")
    val _ = actorSystem.terminate()
  }

  private val REPEAT_N = 1000

  // # Small Data Request

  // API DSL
  val usersPosts =
    API
      .get(In.literal("users") / In.int / "posts" / In.int)
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
      .in("users" / tpath[Int] / "posts" / tpath[Int])
      .in(query[String]("query"))
      .out(jsonBody[ExampleData])

  private val handledUsersPostsEndpoint =
    usersPostsEndpoint.serverLogic[Future] { case (userId, postId, query) =>
      Future.successful(Right(ExampleData(userId, postId, query)))
    }

  val usersPostsRoute: Route =
    AkkaHttpServerInterpreter().toRoute(handledUsersPostsEndpoint)

  val smallDataTapirAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(usersPostsRoute)

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

  // Akka Server

  import akka.http.scaladsl.model._
  import akka.http.scaladsl.server.Directives._

  val smallDataAkkaRoute =
    path("users" / IntNumber / "posts" / IntNumber) { (userId, postId) =>
      get {
        parameters("query") { query =>
          complete(
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                ExampleData(userId, postId, query).toJson,
              ),
            ),
          )
        }
      }
    }

  val smallDataAkkaFunction = Route.toFunction(smallDataAkkaRoute)

//  @Benchmark
//  def benchmarkSmallDataZioApi(): Unit =
//    unsafeRun {
//      apiHttpApp(smallDataRequest).repeatN(REPEAT_N)
//    }
//
//  @Benchmark
//  def benchmarkSmallDataZioCollect(): Unit =
//    unsafeRun {
//      collectHttpApp(smallDataRequest).repeatN(REPEAT_N)
//    }

//  @Benchmark
//  def benchmarkSmallDataTapirAkka(): Unit = {
//    val _ =
//      Await.result(
//        repeatNFuture(REPEAT_N)(smallDataTapirAkkaFunction(smallDataRequestAkka)),
//        scala.concurrent.duration.Duration.Inf,
//      )
//  }
//
//  @Benchmark
//  def benchmarkSmallDataTapirHttp4s(): Unit =
//    unsafeRun {
//      usersPostsHttp4sRoute(smallDataRequestHttp4s).value.repeatN(REPEAT_N)
//    }
//
//  @Benchmark
//  def benchmarkSmallDataAkka(): Unit = {
//    val _ =
//      Await.result(
//        repeatNFuture(REPEAT_N)(smallDataAkkaFunction(smallDataRequestAkka)),
//        scala.concurrent.duration.Duration.Inf,
//      )
//  }

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
      val _ = (id1.toInt, id2.toInt, id3.toInt, id4.toInt, id5.toInt, id6.toInt, id7.toInt)
      ZIO.unit
  }

  // Tapir Akka DSL

  val deepPathEndpoint: Endpoint[Unit, (Int, Int, Int, Int, Int, Int, Int), Unit, Unit, Any] =
    endpoint
      .in(
        "first" / tpath[Int] / "second" / tpath[Int] / "third" / tpath[Int] / "fourth" / tpath[Int] / "fifth" /
          tpath[Int] / "sixth" / tpath[Int] / "seventh" / tpath[Int],
      )

  private val handledDeepPathEndpoint =
    deepPathEndpoint.serverLogic[Future] { _ =>
      Future.successful(Right(()))
    }

  val deepPathRoute: Route =
    AkkaHttpServerInterpreter().toRoute(handledDeepPathEndpoint)

  val deepPathTapirAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(deepPathRoute)

  // Tapir Http4s DSL

  val handledDeepPathEndpointHttp4s =
    deepPathEndpoint.serverLogic[Task] { _ =>
      ZIO.succeed(Right(()))
    }

  val deepPathHttp4sRoute: HttpRoutes[Task] =
    Http4sServerInterpreter[Task]().toRoutes(handledDeepPathEndpointHttp4s)

  val (deepPathRequest, deepPathRequestAkka, deepPathRequestHttp4s) = requestsFromString(
    "http://localhost:8080/first/1/second/2/third/3/fourth/4/fifth/5/sixth/6/seventh/7",
  )

  // Akka Server

  val deepPathAkkaRoute =
    path(
      "first" / IntNumber / "second" / IntNumber / "third" / IntNumber / "fourth" / IntNumber / "fifth" / IntNumber / "sixth" / IntNumber / "seventh" / IntNumber,
    ) { (_, _, _, _, _, _, _) =>
      get {
        complete(HttpResponse())
      }
    }

  val deepPathAkkaFunction = Route.toFunction(deepPathAkkaRoute)

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

//  @Benchmark
//  def benchmarkDeepPathTapirAkka(): Unit   = {
//    val _ = Await.result(
//      repeatNFuture(REPEAT_N)(deepPathTapirAkkaFunction(deepPathRequestAkka)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }
//  @Benchmark
//  def benchmarkDeepPathTapirHttp4s(): Unit =
//    unsafeRun {
//      deepPathHttp4sRoute(deepPathRequestHttp4s).value.repeatN(REPEAT_N)
//    }
//
//  @Benchmark
//  def benchmarkDeepPathAkka(): Unit = {
//    val _ = Await.result(
//      repeatNFuture(REPEAT_N)(deepPathAkkaFunction(deepPathRequestAkka)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }

  // # Broad Path

  // users / 1 / posts / 1 / comments / 1
  // users / 1 / posts / 1 / comments
  // users / 1 / posts / 1
  // users / 1 / posts
  // users / 1
  // users
  // posts / 1 / comments / 1
  // posts / 1 / comments
  // posts / 1
  // posts
  // comments / 1
  // comments
  // users / 1 / comments / 1
  // users / 1 / comments
  // users / 1 / posts / 1 / comments / 1 / replies / 1
  // users / 1 / posts / 1 / comments / 1 / replies

  // API DSL

  val broadUsers                       = API.get(In.literal("users")).handle { _ => ZIO.unit }
  val broadUsersId                     = API.get(In.literal("users") / In.int).handle { _ => ZIO.unit }
  val boardUsersPosts                  =
    API.get(In.literal("users") / In.int / In.literal("posts")).handle { _ => ZIO.unit }
  val boardUsersPostsId                =
    API.get(In.literal("users") / In.int / In.literal("posts") / In.int).handle { _ => ZIO.unit }
  val boardUsersPostsComments          =
    API.get(In.literal("users") / In.int / In.literal("posts") / In.int / In.literal("comments")).handle { _ =>
      ZIO.unit
    }
  val boardUsersPostsCommentsId        =
    API.get(In.literal("users") / In.int / In.literal("posts") / In.int / In.literal("comments") / In.int).handle { _ =>
      ZIO.unit
    }
  val broadPosts                       = API.get(In.literal("posts")).handle { _ => ZIO.unit }
  val broadPostsId                     = API.get(In.literal("posts") / In.int).handle { _ => ZIO.unit }
  val boardPostsComments               =
    API.get(In.literal("posts") / In.int / In.literal("comments")).handle { _ => ZIO.unit }
  val boardPostsCommentsId             =
    API.get(In.literal("posts") / In.int / In.literal("comments") / In.int).handle { _ => ZIO.unit }
  val broadComments                    = API.get(In.literal("comments")).handle { _ => ZIO.unit }
  val broadCommentsId                  = API.get(In.literal("comments") / In.int).handle { _ => ZIO.unit }
  val broadUsersComments               =
    API.get(In.literal("users") / In.int / In.literal("comments")).handle { _ => ZIO.unit }
  val broadUsersCommentsId             =
    API.get(In.literal("users") / In.int / In.literal("comments") / In.int).handle { _ => ZIO.unit }
  val boardUsersPostsCommentsReplies   =
    API
      .get(
        In.literal("users") / In.int / In.literal("posts") / In.int / In.literal("comments") / In.int / In.literal(
          "replies",
        ),
      )
      .handle { _ =>
        ZIO.unit
      }
  val boardUsersPostsCommentsRepliesId =
    API
      .get(
        In.literal("users") / In.int / In.literal("posts") / In.int / In.literal("comments") / In.int / In.literal(
          "replies",
        ) / In.int,
      )
      .handle { _ =>
        ZIO.unit
      }

  val broadApiApp =
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
        broadCommentsId ++
        broadUsersComments ++
        broadUsersCommentsId ++
        boardUsersPostsCommentsReplies ++
        boardUsersPostsCommentsRepliesId
    ).toHttpApp

  // Collect DSL

  val broadCollectApp = Http.collectZIO[Request] {
    case Method.GET -> !! / "users" / userId / "posts" / postId / "comments" / commentId                       =>
      val _ = (userId.toInt, postId.toInt, commentId.toInt)
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts" / postId / "comments"                                   =>
      val _ = (userId.toInt, postId.toInt)
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts" / postId                                                =>
      val _ = (userId.toInt, postId.toInt)
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts"                                                         =>
      val _ = userId.toInt
      ZIO.unit
    case Method.GET -> !! / "users" / userId                                                                   =>
      val _ = userId.toInt
      ZIO.unit
    case Method.GET -> !! / "users"                                                                            =>
      ZIO.unit
    case Method.GET -> !! / "posts" / postId / "comments" / commentId                                          =>
      val _ = (postId.toInt, commentId.toInt)
      ZIO.unit
    case Method.GET -> !! / "posts" / postId / "comments"                                                      =>
      val _ = postId.toInt
      ZIO.unit
    case Method.GET -> !! / "posts" / postId                                                                   =>
      val _ = postId.toInt
      ZIO.unit
    case Method.GET -> !! / "posts"                                                                            =>
      ZIO.unit
    case Method.GET -> !! / "comments" / commentId                                                             =>
      val _ = commentId.toInt
      ZIO.unit
    case Method.GET -> !! / "comments"                                                                         =>
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "comments"                                                      =>
      val _ = userId.toInt
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "comments" / commentId                                          =>
      val _ = (userId.toInt, commentId.toInt)
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts" / postId / "comments" / commentId / "replies" / replyId =>
      val _ = (userId.toInt, postId.toInt, commentId.toInt, replyId.toInt)
      ZIO.unit
    case Method.GET -> !! / "users" / userId / "posts" / postId / "comments" / commentId / "replies"           =>
      val _ = (userId.toInt, postId.toInt, commentId.toInt)
      ZIO.unit
  }

  // Tapir Akka DSL

  val broadTapirUsers                       = endpoint.get.in("users")
  val broadTapirUsersId                     = endpoint.get.in("users" / tpath[Int])
  val broadTapirUsersPosts                  = endpoint.get.in("users" / tpath[Int] / "posts")
  val broadTapirUsersPostsId                = endpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int])
  val broadTapirUsersPostsComments          = endpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments")
  val broadTapirUsersPostsCommentsId        =
    endpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments" / tpath[Int])
  val broadTapirPosts                       = endpoint.get.in("posts")
  val broadTapirPostsId                     = endpoint.get.in("posts" / tpath[Int])
  val broadTapirPostsComments               = endpoint.get.in("posts" / tpath[Int] / "comments")
  val broadTapirPostsCommentsId             = endpoint.get.in("posts" / tpath[Int] / "comments" / tpath[Int])
  val broadTapirComments                    = endpoint.get.in("comments")
  val broadTapirCommentsId                  = endpoint.get.in("comments" / tpath[Int])
  val broadTapirUsersComments               = endpoint.get.in("users" / tpath[Int] / "comments")
  val broadTapirUsersCommentsId             = endpoint.get.in("users" / tpath[Int] / "comments" / tpath[Int])
  val broadTapirUsersPostsCommentsReplies   =
    endpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments" / tpath[Int] / "replies")
  val broadTapirUsersPostsCommentsRepliesId =
    endpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments" / tpath[Int] / "replies" / tpath[Int])

  val broadTapirAkkaApp =
    AkkaHttpServerInterpreter()
      .toRoute(
        List(
          broadTapirUsers.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersPosts.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersPostsId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersPostsComments.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersPostsCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirPosts.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirPostsId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirPostsComments.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirPostsCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirComments.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersComments.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersPostsCommentsReplies.serverLogic[Future](_ => Future.successful(Right(()))),
          broadTapirUsersPostsCommentsRepliesId.serverLogic[Future](_ => Future.successful(Right(()))),
        ),
      )

  val boardTapirAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(broadTapirAkkaApp)

  // Tapir Http4s DSL

  val broadTapirHttp4sApp =
    Http4sServerInterpreter[Task]()
      .toRoutes(
        List(
          broadTapirUsers.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersPosts.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersPostsId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersPostsComments.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersPostsCommentsId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirPosts.serverLogic[Task](_ => ZIO.right(())),
          broadTapirPostsId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirPostsComments.serverLogic[Task](_ => ZIO.right(())),
          broadTapirPostsCommentsId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirComments.serverLogic[Task](_ => ZIO.right(())),
          broadTapirCommentsId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersComments.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersCommentsId.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersPostsCommentsReplies.serverLogic[Task](_ => ZIO.right(())),
          broadTapirUsersPostsCommentsRepliesId.serverLogic[Task](_ => ZIO.right(())),
        ),
      )

  // Akka

  val broadAkkaRoute =
    concat(
      path("users") { get { complete(HttpResponse()) } },
      path("users" / IntNumber) { _ => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "posts") { _ => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "posts" / IntNumber) { (_, _) => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "posts" / IntNumber / "comments") { (_, _) => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "posts" / IntNumber / "comments" / IntNumber) { (_, _, _) =>
        get { complete(HttpResponse()) }
      },
      path("posts") { get { complete(HttpResponse()) } },
      path("posts" / IntNumber) { _ => get { complete(HttpResponse()) } },
      path("posts" / IntNumber / "comments") { _ => get { complete(HttpResponse()) } },
      path("posts" / IntNumber / "comments" / IntNumber) { (_, _) => get { complete(HttpResponse()) } },
      path("comments") { get { complete(HttpResponse()) } },
      path("comments" / IntNumber) { _ => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "comments") { _ => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "comments" / IntNumber) { (_, _) => get { complete(HttpResponse()) } },
      path("users" / IntNumber / "posts" / IntNumber / "comments" / IntNumber / "replies") { (_, _, _) =>
        get { complete(HttpResponse()) }
      },
      path("users" / IntNumber / "posts" / IntNumber / "comments" / IntNumber / "replies" / IntNumber) { (_, _, _, _) =>
        get { complete(HttpResponse()) }
      },
    )

  val broadAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(broadAkkaRoute)

  val requests = Vector(
    "http://localhost:8080/users",
    "http://localhost:8080/users/1",
    "http://localhost:8080/users/1/posts",
    "http://localhost:8080/users/1/posts/1",
    "http://localhost:8080/users/1/posts/1/comments",
    "http://localhost:8080/users/1/posts/1/comments/1",
    "http://localhost:8080/posts",
    "http://localhost:8080/posts/1",
    "http://localhost:8080/posts/1/comments",
    "http://localhost:8080/posts/1/comments/1",
    "http://localhost:8080/comments",
    "http://localhost:8080/comments/1",
    "http://localhost:8080/users/1/comments",
    "http://localhost:8080/users/1/comments/1",
    "http://localhost:8080/users/1/posts/1/comments/1/replies",
    "http://localhost:8080/users/1/posts/1/comments/1/replies/1",
  )

  val randomRequests: Chunk[String] =
    unsafeRunResult {
      Random.setSeed(10) *>
        ZIO.collectAll(Chunk.fill(REPEAT_N) {
          Random.nextIntBounded(requests.length).map(requests(_))
        })
    }

  val broadZioRequests: Chunk[Request]            = randomRequests.map(requestsFromString(_)._1)
  val broadAkkaRequests: Chunk[HttpRequest]       = randomRequests.map(requestsFromString(_)._2)
  val broadHttp4sRequests: Chunk[Request4s[Task]] = randomRequests.map(requestsFromString(_)._3)

  @Benchmark
  def benchmarkBroadZioApi(): Unit =
    unsafeRunResult {
      ZIO.foreachDiscard(broadZioRequests)(broadApiApp(_))
    }

  @Benchmark
  def benchmarkBroadZioCollect(): Unit =
    unsafeRun {
      ZIO.foreachDiscard(broadZioRequests)(broadCollectApp(_))
    }

//  @Benchmark
//  def benchmarkBroadTapirAkka(): Unit = {
//    val _ = Await.result(
//      foreachDiscardFuture(broadAkkaRequests)(boardTapirAkkaFunction(_)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }
//
//  @Benchmark
//  def benchmarkBroadTapirHttp4s(): Unit = {
//    val _ = unsafeRunResult {
//      ZIO.foreachDiscard(broadHttp4sRequests)(broadTapirHttp4sApp(_).value)
//    }
//  }
//
//  @Benchmark
//  def benchmarkBroadAkka(): Unit = {
//    val _ = Await.result(
//      foreachDiscardFuture(broadAkkaRequests)(broadAkkaFunction(_)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }

  private def httpRequestFromString(url: String): Request =
    Request(url = URL.fromString(url).toOption.get)

  private def akkaHttpRequestFromString(url: String): HttpRequest =
    HttpRequest(uri = url)

  private def http4sRequestFromString(url: String): Request4s[Task] =
    Request4s[Task](uri = org.http4s.Uri.unsafeFromString(url))

  private def requestsFromString(url: String): (Request, HttpRequest, Request4s[Task]) =
    (httpRequestFromString(url), akkaHttpRequestFromString(url), http4sRequestFromString(url))

  def unsafeRun[E, A](zio: ZIO[Any, E, A]): Unit = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(zio.unit)
      .getOrThrowFiberFailure()
  }

  private def unsafeRunResult[E, A](zio: ZIO[Any, E, A]): A = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(zio)
      .getOrThrowFiberFailure()
  }

  private def repeatNFuture[A](n: Int)(f: => Future[A]): Future[A] =
    if (n == 0) f else f.flatMap(_ => repeatNFuture(n - 1)(f))

  private def foreachDiscardFuture[A, B](values: Iterable[A])(f: A => Future[B]): Future[Unit] =
    if (values.isEmpty) Future.unit
    else values.tail.foldLeft(f(values.head))((future, a) => future.flatMap(_ => f(a))).flatMap(_ => Future.unit)

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
