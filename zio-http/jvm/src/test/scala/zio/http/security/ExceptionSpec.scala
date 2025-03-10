package zio.http.security

import zio._
import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.netty.NettyConfig

object ExceptionSpec extends ZIOSpecDefault {

  val routesError = Routes(Method.GET / "error" -> Handler.ok.map(_ => throw new Throwable("BOOM!")))
  val routesFail  = Routes(
    Method.GET / "fail" -> Handler.fail(new Throwable("BOOM!")).sandbox.merge,
  )
  val routesDie   = Routes(
    Method.GET / "die" -> Handler.die(new Throwable("BOOM!")).sandbox.merge,
  )

  val queryRoutes =
    Routes(
      Method.GET / "search" -> Handler.fromFunctionHandler { (req: Request) =>
        val response: ZIO[Any, HttpCodecError.QueryParamError, Response] =
          ZIO
            .fromEither(req.query[Int]("age"))
            .map(value => Response.text(s"The value of age query param is: $value"))

        Handler.fromZIO(response).catchAll {
          case HttpCodecError.MissingQueryParams(names) =>
            Handler.badRequest(s"The ${names.head} query param is missing")
          case e: HttpCodecError.MalformedQueryParam    =>
            Handler.badRequest(e.getMessage())
          case e                                        =>
            Handler.badRequest(e.getMessage())
        }
      },
    )

  val spec = suite("ExceptionSpec")(
    test("Bad endpoint doesn't leak stacktrace") {
      // calls `Response.fromCause` that prints the stacktrace
      val badEndpoint = Endpoint(Method.GET / "test")
        .out[String]
      val route       = badEndpoint.implementHandler(Handler.fromFunction { _ => "string" })
      val request     =
        Request.get(url"/test").addHeader(Header.Accept(MediaType.text.`html`))
      for {
        response <- route.toRoutes.runZIO(request).map(_.headers.toString)
      } yield assertTrue(!response.contains("Exception in thread"))
    },
    test("Throw inside handle doesn't leak stacktrace") {
      for {
        port     <- Server.installRoutes(routesError)
        response <- ZIO.scoped {
          Client.streaming(Request.get(s"http://localhost:$port/error")).flatMap(_.ignoreBody).map(_.headers.toString)
        }
      } yield assertTrue(!response.contains("Exception in thread"))
    },
    test("Die handle doesn't leak stacktrace") {
      for {
        port     <- Server.installRoutes(routesDie)
        response <- ZIO.scoped {
          Client.streaming(Request.get(s"http://localhost:$port/die")).flatMap(_.ignoreBody).map(_.headers.toString)
        }
      } yield assertTrue(!response.contains("Exception in thread"))
    },
    test("Failing handle doesn't leak stacktrace") {
      for {
        port     <- Server.installRoutes(routesFail)
        response <- ZIO.scoped {
          Client.streaming(Request.get(s"http://localhost:$port/fail")).flatMap(_.ignoreBody).map(_.headers.toString)
        }
      } yield assertTrue(!response.contains("Exception in thread"))
    },
    test("FromZIO doesn't leak stacktrace") {
      for {
        port     <- Server.installRoutes(queryRoutes)
        response <- ZIO.scoped {
          Client.streaming(Request.get(s"http://localhost:$port/search")).flatMap(_.ignoreBody).map(_.headers.toString)
        }
      } yield assertTrue(!response.contains("Exception in thread"))
    },
  ).provide(
    Scope.default,
    Server.customized,
    ZLayer.succeed(
      Server.Config.default,
    ),
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    Client.default,
  ) @@ TestAspect.sequential
}
