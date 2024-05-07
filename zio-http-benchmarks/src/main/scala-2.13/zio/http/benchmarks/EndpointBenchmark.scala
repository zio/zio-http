package zio.http.benchmarks

//import akka.actor.ActorSystem
//import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
//import akka.http.scaladsl.server.Route
import java.util.concurrent.TimeUnit

import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec}
import zio.{Scope => _, _}

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.QueryCodec
import zio.http.endpoint.Endpoint

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.{HttpRoutes, Request => Request4s}
import org.openjdk.jmh.annotations._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.{Endpoint => TEndpoint, endpoint => tendpoint, path => tpath, _}

// Original
//
// [info] EndpointBenchmark.benchmarkBroadTapirHttp4s      thrpt    2  115.028          ops/s
// [info] EndpointBenchmark.benchmarkBroadZioApi           thrpt    2  751.619          ops/s
// [info] EndpointBenchmark.benchmarkBroadZioCollect       thrpt    2  126.598          ops/s
//
// [info] EndpointBenchmark.benchmarkDeepPathTapirHttp4s   thrpt    2   65.697          ops/s
// [info] EndpointBenchmark.benchmarkDeepPathZioApi        thrpt    2  565.441          ops/s
// [info] EndpointBenchmark.benchmarkDeepPathZioCollect    thrpt    2  956.824          ops/s
//
// [info] EndpointBenchmark.benchmarkSmallDataTapirHttp4s  thrpt    2   92.525          ops/s
// [info] EndpointBenchmark.benchmarkSmallDataZioApi       thrpt    2  277.247          ops/s
// [info] EndpointBenchmark.benchmarkSmallDataZioCollect   thrpt    2  703.899          ops/s
//
//
// After Branch optimization
//
// [info] EndpointBenchmark.benchmarkBroadTapirHttp4s      thrpt    2  118.398          ops/s
// [info] EndpointBenchmark.benchmarkBroadZioApi           thrpt    2  979.155          ops/s (up from 751)
// [info] EndpointBenchmark.benchmarkBroadZioCollect       thrpt    2  122.202          ops/s
//
// [info] EndpointBenchmark.benchmarkDeepPathTapirHttp4s   thrpt    2   65.647          ops/s
// [info] EndpointBenchmark.benchmarkDeepPathZioApi        thrpt    2  563.393          ops/s
// [info] EndpointBenchmark.benchmarkDeepPathZioCollect    thrpt    2  977.179          ops/s
//
// [info] EndpointBenchmark.benchmarkSmallDataTapirHttp4s  thrpt    2   91.951          ops/s
// [info] EndpointBenchmark.benchmarkSmallDataZioApi       thrpt    2  264.240          ops/s
// [info] EndpointBenchmark.benchmarkSmallDataZioCollect   thrpt    2  698.570          ops/s
//
//
// After `hasBody` check
//
// [info] EndpointBenchmark.benchmarkBroadTapirHttp4s      thrpt    2   113.092          ops/s
// [info] EndpointBenchmark.benchmarkBroadZioApi           thrpt    2  1745.498          ops/s
// [info] EndpointBenchmark.benchmarkBroadZioCollect       thrpt    2   125.484          ops/s
//
// [info] EndpointBenchmark.benchmarkDeepPathTapirHttp4s   thrpt    2    66.033          ops/s
// [info] EndpointBenchmark.benchmarkDeepPathZioApi        thrpt    2   781.366          ops/s
// [info] EndpointBenchmark.benchmarkDeepPathZioCollect    thrpt    2   944.260          ops/s
//
// [info] EndpointBenchmark.benchmarkSmallDataTapirHttp4s  thrpt    2    87.859          ops/s
// [info] EndpointBenchmark.benchmarkSmallDataZioApi       thrpt    2   267.333          ops/s
// [info] EndpointBenchmark.benchmarkSmallDataZioCollect   thrpt    2   701.566          ops/s

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class EndpointBenchmark {
//  implicit val actorSystem: ActorSystem     = ActorSystem("api-benchmark-actor-system")
//  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
//
//  @TearDown
//  def shutdown(): Unit = {
//    println("\n\n Shutting down actor system \n\n")
//    val _ = actorSystem.terminate()
//  }

  private val REPEAT_N = 1000

  import zio.http.codec.PathCodec.int

  // # Small Data Request

  // API DSL
  val usersPosts =
    Endpoint(Method.GET / "users" / int("userId") / "posts" / int("limit"))
      .query(QueryCodec.query("query"))
      .out[ExampleData]

  val handledUsersPosts =
    usersPosts.implement {
      Handler.fromFunction { case (userId, postId, limit) =>
        ExampleData(userId, postId, limit)
      }
    }

  val apiRoutes = handledUsersPosts.toRoutes

  // Collect DSL
  val collectdRoutes = Routes(
    Method.GET / "users" / int("userId") / "posts" / int("postId") -> handler {
      (userIdInt: Int, postIdInt: Int, req: Request) =>
        val query = req.url.queryParam("query").get

        Response.json(ExampleData(userIdInt, postIdInt, query).toJson)
    },
  )

  // Tapir Akka DSL

  val usersPostsEndpoint: TEndpoint[Unit, (Int, Int, String), Unit, ExampleData, Any] =
    tendpoint
      .in("users" / tpath[Int] / "posts" / tpath[Int])
      .in(query[String]("query"))
      .out(jsonBody[ExampleData])

//  private val handledUsersPostsEndpoint =
//    usersPostsEndpoint.serverLogic[Future] { case (userId, postId, query) =>
//      Future.successful(Right(ExampleData(userId, postId, query)))
//    }

//  val usersPostsRoute: Route =
//    AkkaHttpServerInterpreter().toRoute(handledUsersPostsEndpoint)
//
//  val smallDataTapirAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(usersPostsRoute)

  // Tapir Http4s DSL

  val handledUsersPostsEndpointHttp4s =
    usersPostsEndpoint.serverLogic[CIO] { case (userId, postId, query) =>
      CIO.pure(Right(ExampleData(userId, postId, query)))
    }

  val usersPostsHttp4sRoute: HttpRoutes[CIO] =
    Http4sServerInterpreter[CIO]().toRoutes(handledUsersPostsEndpointHttp4s)

  val (smallDataRequest, _, smallDataRequestHttp4s) = requestsFromString(
    "http://localhost:8080/users/1/posts/2?query=cool",
  )

  // Akka Server

//  import akka.http.scaladsl.model._
//  import akka.http.scaladsl.server.Directives._
//
//  val smallDataAkkaRoute =
//    path("users" / IntNumber / "posts" / IntNumber) { (userId, postId) =>
//      get {
//        parameters("query") { query =>
//          complete(
//            HttpResponse(
//              entity = HttpEntity(
//                ContentTypes.`application/json`,
//                ExampleData(userId, postId, query).toJson,
//              ),
//            ),
//          )
//        }
//      }
//    }
//
//  val smallDataAkkaFunction = Route.toFunction(smallDataAkkaRoute)

  @Benchmark
  def benchmarkSmallDataZioApi(): Unit =
    unsafeRun {
      apiRoutes.runZIO(smallDataRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkSmallDataZioCollect(): Unit =
    unsafeRun {
      collectdRoutes.runZIO(smallDataRequest).repeatN(REPEAT_N)
    }

//  @Benchmark
//  def benchmarkSmallDataTapirAkka(): Unit = {
//    val _ =
//      Await.result(
//        repeatNFuture(REPEAT_N)(smallDataTapirAkkaFunction(smallDataRequestAkka)),
//        scala.concurrent.duration.Duration.Inf,
//      )
//  }

  @Benchmark
  def benchmarkSmallDataTapirHttp4s(): Unit =
    usersPostsHttp4sRoute(smallDataRequestHttp4s).value
      .replicateA_(REPEAT_N)
      .unsafeRunSync()

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

  val deepPathRoutes = Endpoint(
    Method.GET /
      "first" /
      int("id1") / "second" / int("id2") / "third" / int(
        "id3",
      ) / "fourth" / int("id4") / "fifth" / int("id5") / "sixth" / int(
        "id5",
      ) / "seventh" / int("id5"),
  )
    .out[Unit]
    .implement(Handler.unit)
    .toRoutes

  // Collect DSL

  val deepPathCollectHttpApp = Routes(
    Method.GET / "first" / int("id1") / "second" / int("id2") / "third" / int("id3") / "fourth" / int(
      "id4",
    ) / "fifth" / int("id5") / "sixth" / int("id6") / "seventh" / int("id7") ->
      handler { (_: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Request) =>
        ZIO.succeed(Response.ok)
      },
  )

  // Tapir Akka DSL

  val deepPathEndpoint: TEndpoint[Unit, (Int, Int, Int, Int, Int, Int, Int), Unit, Unit, Any] =
    tendpoint
      .in(
        "first" / tpath[Int] / "second" / tpath[Int] / "third" / tpath[Int] / "fourth" / tpath[Int] / "fifth" /
          tpath[Int] / "sixth" / tpath[Int] / "seventh" / tpath[Int],
      )

//  private val handledDeepPathEndpoint =
//    deepPathEndpoint.serverLogic[Future] { _ =>
//      Future.successful(Right(()))
//    }

//  val deepPathRoute: Route =
//    AkkaHttpServerInterpreter().toRoute(handledDeepPathEndpoint)
//
//  val deepPathTapirAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(deepPathRoute)

  // Tapir Http4s DSL

  val handledDeepPathEndpointHttp4s =
    deepPathEndpoint.serverLogic[CIO] { _ =>
      CIO.pure(Right(()))
    }

  val deepPathHttp4sRoute: HttpRoutes[CIO] =
    Http4sServerInterpreter[CIO]().toRoutes(handledDeepPathEndpointHttp4s)

  val (deepPathRequest, deepPathRequestAkka, deepPathRequestHttp4s) = requestsFromString(
    "http://localhost:8080/first/1/second/2/third/3/fourth/4/fifth/5/sixth/6/seventh/7",
  )

  // Akka Server

//  val deepPathAkkaRoute =
//    path(
//      "first" / IntNumber / "second" / IntNumber / "third" / IntNumber / "fourth" / IntNumber / "fifth" / IntNumber / "sixth" / IntNumber / "seventh" / IntNumber,
//    ) { (_, _, _, _, _, _, _) =>
//      get {
//        complete(HttpResponse())
//      }
//    }
//
//  val deepPathAkkaFunction = Route.toFunction(deepPathAkkaRoute)

  @Benchmark
  def benchmarkDeepPathZioApi(): Unit =
    unsafeRun {
      deepPathRoutes.runZIO(deepPathRequest).repeatN(REPEAT_N)
    }

  @Benchmark
  def benchmarkDeepPathZioCollect(): Unit =
    unsafeRun {
      deepPathCollectHttpApp.runZIO(deepPathRequest).repeatN(REPEAT_N)
    }

//  @Benchmark
//  def benchmarkDeepPathTapirAkka(): Unit = {
//    val _ = Await.result(
//      repeatNFuture(REPEAT_N)(deepPathTapirAkkaFunction(deepPathRequestAkka)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }

  @Benchmark
  def benchmarkDeepPathTapirHttp4s(): Unit =
    deepPathHttp4sRoute(deepPathRequestHttp4s).value
      .replicateA_(REPEAT_N)
      .unsafeRunSync()

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

  val broadUsers                       = Endpoint(Method.GET / "users").out[Unit].implement(Handler.unit)
  val broadUsersId                     =
    Endpoint(Method.GET / "users" / int("userId")).out[Unit].implement(Handler.unit)
  val boardUsersPosts                  =
    Endpoint(Method.GET / "users" / int("userId") / "posts")
      .out[Unit]
      .implement(Handler.unit)
  val boardUsersPostsId                =
    Endpoint(
      Method.GET /
        "users" / int("userId") / "posts" / int("postId"),
    )
      .out[Unit]
      .implement(Handler.unit)
  val boardUsersPostsComments          =
    Endpoint(
      Method.GET /
        "users" / int("userId") / "posts" / int(
          "postId",
        ) / "comments",
    )
      .out[Unit]
      .implement(Handler.unit)
  val boardUsersPostsCommentsId        =
    Endpoint(
      Method.GET /
        "users" / int("userId") / "posts" / int(
          "postId",
        ) / "comments" / int("commentId"),
    )
      .out[Unit]
      .implement(Handler.unit)
  val broadPosts                       = Endpoint(Method.GET / "posts").out[Unit].implement(Handler.unit)
  val broadPostsId                     =
    Endpoint(Method.GET / "posts" / int("postId")).out[Unit].implement(Handler.unit)
  val boardPostsComments               =
    Endpoint(Method.GET / "posts" / int("postId") / "comments")
      .out[Unit]
      .implement(Handler.unit)
  val boardPostsCommentsId             =
    Endpoint(
      Method.GET /
        "posts" / int("postId") / "comments" / int(
          "commentId",
        ),
    )
      .out[Unit]
      .implement(Handler.unit)
  val broadComments                    = Endpoint(Method.GET / "comments").out[Unit].implement(Handler.unit)
  val broadCommentsId                  =
    Endpoint(Method.GET / "comments" / int("commentId")).out[Unit].implement(Handler.unit)
  val broadUsersComments               =
    Endpoint(Method.GET / "users" / int("userId") / "comments")
      .out[Unit]
      .implement(Handler.unit)
  val broadUsersCommentsId             =
    Endpoint(
      Method.GET /
        "users" / int("userId") / "comments" / int(
          "commentId",
        ),
    )
      .out[Unit]
      .implement(Handler.unit)
  val boardUsersPostsCommentsReplies   =
    Endpoint(
      Method.GET /
        "users" / int("userId") / "posts" / int(
          "postId",
        ) / "comments" / int("commentId") /
        "replies",
    )
      .out[Unit]
      .implement(Handler.unit)
  val boardUsersPostsCommentsRepliesId =
    Endpoint(
      Method.GET /
        "users" / int("userId") / "posts" / int(
          "postId",
        ) / "comments" / int("commentId") / "replies" / int("replyId"),
    )
      .out[Unit]
      .implement(Handler.unit)

  val broadApiApp =
    Routes(
      broadUsers,
      broadUsersId,
      boardUsersPosts,
      boardUsersPostsId,
      boardUsersPostsComments,
      boardUsersPostsCommentsId,
      broadPosts,
      broadPostsId,
      boardPostsComments,
      boardPostsCommentsId,
      broadComments,
      broadCommentsId,
      broadUsersComments,
      broadUsersCommentsId,
      boardUsersPostsCommentsReplies,
      boardUsersPostsCommentsRepliesId,
    )

  // Collect DSL

  val broadCollectApp = Routes(
    Method.GET / "users" / int("userId") / "posts" / int("postId") / "comments" / int("commentId") -> handler {
      (_: Int, _: Int, _: Int, _: Request) =>
        Response()
    },
    Method.GET / "users" / int("userId") / "posts" / int("postId") / "comments"                    -> handler {
      (_: Int, _: Int, _: Request) =>
        Response()
    },
    Method.GET / "users" / int("userId") / "posts" / int("postId")       -> handler { (_: Int, _: Int, _: Request) =>
      Response()
    },
    Method.GET / "users" / int("userId") / "posts"                       -> handler { (_: Int, _: Request) =>
      Response()
    },
    Method.GET / "users" / int("userId")                                 -> handler { (_: Int, _: Request) =>
      Response()
    },
    Method.GET / "users"                                                 -> handler(Response()),
    Method.GET / "posts" / int("postId") / "comments" / int("commentId") -> handler { (_: Int, _: Int, _: Request) =>
      Response()
    },
    Method.GET / "posts" / int("postId") / "comments"                    -> handler { (_: Int, _: Request) =>
      Response()
    },
    Method.GET / "posts" / int("postId")                                 -> handler { (_: Int, _: Request) =>
      Response()
    },
    Method.GET / "posts"                                                 -> handler(Response()),
    Method.GET / "comments" / int("commentId")                           -> handler { (_: Int, _: Request) =>
      Response()
    },
    Method.GET / "comments"                                              -> handler(Response()),
    Method.GET / "users" / int("userId") / "comments"                    -> handler { (_: Int, _: Request) =>
      Response()
    },
    Method.GET / "users" / int("userId") / "comments" / int("commentId") -> handler { (_: Int, _: Int, _: Request) =>
      Response()
    },
    Method.GET / "users" / int("userId") / "posts" / int("postId") / "comments" / int("commentId") / "replies" / int(
      "replyId",
    )             -> handler { (_: Int, _: Int, _: Int, _: Int, _: Request) =>
      Response()
    },
    Method.GET / "users" / int("userId") / "posts" / int("postId") / "comments" / int(
      "commentId",
    ) / "replies" -> handler { (_: Int, _: Int, _: Int, _: Request) =>
      Response()
    },
  )

  // Tapir Akka DSL

  val broadTapirUsers                       = tendpoint.get.in("users")
  val broadTapirUsersId                     = tendpoint.get.in("users" / tpath[Int])
  val broadTapirUsersPosts                  = tendpoint.get.in("users" / tpath[Int] / "posts")
  val broadTapirUsersPostsId                = tendpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int])
  val broadTapirUsersPostsComments          = tendpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments")
  val broadTapirUsersPostsCommentsId        =
    tendpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments" / tpath[Int])
  val broadTapirPosts                       = tendpoint.get.in("posts")
  val broadTapirPostsId                     = tendpoint.get.in("posts" / tpath[Int])
  val broadTapirPostsComments               = tendpoint.get.in("posts" / tpath[Int] / "comments")
  val broadTapirPostsCommentsId             = tendpoint.get.in("posts" / tpath[Int] / "comments" / tpath[Int])
  val broadTapirComments                    = tendpoint.get.in("comments")
  val broadTapirCommentsId                  = tendpoint.get.in("comments" / tpath[Int])
  val broadTapirUsersComments               = tendpoint.get.in("users" / tpath[Int] / "comments")
  val broadTapirUsersCommentsId             = tendpoint.get.in("users" / tpath[Int] / "comments" / tpath[Int])
  val broadTapirUsersPostsCommentsReplies   =
    tendpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments" / tpath[Int] / "replies")
  val broadTapirUsersPostsCommentsRepliesId =
    tendpoint.get.in("users" / tpath[Int] / "posts" / tpath[Int] / "comments" / tpath[Int] / "replies" / tpath[Int])

//  val broadTapirAkkaApp =
//    AkkaHttpServerInterpreter()
//      .toRoute(
//        List(
//          broadTapirUsers.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersPosts.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersPostsId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersPostsComments.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersPostsCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirPosts.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirPostsId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirPostsComments.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirPostsCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirComments.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersComments.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersCommentsId.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersPostsCommentsReplies.serverLogic[Future](_ => Future.successful(Right(()))),
//          broadTapirUsersPostsCommentsRepliesId.serverLogic[Future](_ => Future.successful(Right(()))),
//        ),
//      )
//
//  val boardTapirAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(broadTapirAkkaApp)

  // Tapir Http4s DSL

  val broadTapirHttp4sApp =
    Http4sServerInterpreter[CIO]()
      .toRoutes(
        List(
          broadTapirUsers.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersPosts.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersPostsId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersPostsComments.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersPostsCommentsId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirPosts.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirPostsId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirPostsComments.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirPostsCommentsId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirComments.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirCommentsId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersComments.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersCommentsId.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersPostsCommentsReplies.serverLogic[CIO](_ => CIO.pure(Right(()))),
          broadTapirUsersPostsCommentsRepliesId.serverLogic[CIO](_ => CIO.pure(Right(()))),
        ),
      )

  // Akka

//  val broadAkkaRoute =
//    concat(
//      path("users") { get { complete(HttpResponse()) } },
//      path("users" / IntNumber) { _ => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "posts") { _ => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "posts" / IntNumber) { (_, _) => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "posts" / IntNumber / "comments") { (_, _) => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "posts" / IntNumber / "comments" / IntNumber) { (_, _, _) =>
//        get { complete(HttpResponse()) }
//      },
//      path("posts") { get { complete(HttpResponse()) } },
//      path("posts" / IntNumber) { _ => get { complete(HttpResponse()) } },
//      path("posts" / IntNumber / "comments") { _ => get { complete(HttpResponse()) } },
//      path("posts" / IntNumber / "comments" / IntNumber) { (_, _) => get { complete(HttpResponse()) } },
//      path("comments") { get { complete(HttpResponse()) } },
//      path("comments" / IntNumber) { _ => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "comments") { _ => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "comments" / IntNumber) { (_, _) => get { complete(HttpResponse()) } },
//      path("users" / IntNumber / "posts" / IntNumber / "comments" / IntNumber / "replies") { (_, _, _) =>
//        get { complete(HttpResponse()) }
//      },
//      path("users" / IntNumber / "posts" / IntNumber / "comments" / IntNumber / "replies" / IntNumber) { (_, _, _, _) =>
//        get { complete(HttpResponse()) }
//      },
//    )
//
//  val broadAkkaFunction: HttpRequest => Future[HttpResponse] = Route.toFunction(broadAkkaRoute)

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

  val broadZioRequests: Chunk[Request]           = randomRequests.map(requestsFromString(_)._1)
//  val broadAkkaRequests: Chunk[HttpRequest]       = randomRequests.map(requestsFromString(_)._2)
  val broadHttp4sRequests: Chunk[Request4s[CIO]] = randomRequests.map(requestsFromString(_)._3)

  @Benchmark
  def benchmarkBroadZioApi(): Unit =
    unsafeRunResult {
      ZIO.foreachDiscard(broadZioRequests)(broadApiApp.runZIO(_))
    }

  @Benchmark
  def benchmarkBroadZioCollect(): Unit =
    unsafeRun {
      ZIO.foreachDiscard(broadZioRequests)(broadCollectApp.runZIO(_))
    }

//  @Benchmark
//  def benchmarkBroadTapirAkka(): Unit = {
//    val _ = Await.result(
//      foreachDiscardFuture(broadAkkaRequests)(boardTapirAkkaFunction(_)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }
//
  @Benchmark
  def benchmarkBroadTapirHttp4s(): Unit = {
    val _ = foreachDiscardCIO(broadHttp4sRequests) { req =>
      broadTapirHttp4sApp(req).value
    }.unsafeRunSync()
  }
//
//  @Benchmark
//  def benchmarkBroadAkka(): Unit = {
//    val _ = Await.result(
//      foreachDiscardFuture(broadAkkaRequests)(broadAkkaFunction(_)),
//      scala.concurrent.duration.Duration.Inf,
//    )
//  }

  private def httpRequestFromString(url: String): Request =
    Request.get(url = URL.decode(url).toOption.get)

//  private def akkaHttpRequestFromString(url: String): HttpRequest =
//    HttpRequest(uri = url)

  private def http4sRequestFromString(url: String): Request4s[CIO] =
    Request4s[CIO](uri = org.http4s.Uri.unsafeFromString(url))

  private def requestsFromString(url: String): (Request, Unit, Request4s[CIO]) =
    (
      httpRequestFromString(url),
      (), // akkaHttpRequestFromString(url),
      http4sRequestFromString(url),
    )

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

//  private def repeatNFuture[A](n: Int)(f: => Future[A])(implicit ec: ExecutionContext): Future[A] =
//    if (n == 0) f else f.flatMap(_ => repeatNFuture(n - 1)(f))

  private def foreachDiscardCIO[A, B](
    values: Iterable[A],
  )(f: A => CIO[B]): CIO[Unit] = {
    val list = values.toList
    list.tail
      .foldLeft[CIO[Any]](CIO.pure(list.head)) { (acc, a) =>
        acc *> f(a)
      }
      .flatMap(_ => CIO.pure(()))
  }

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
