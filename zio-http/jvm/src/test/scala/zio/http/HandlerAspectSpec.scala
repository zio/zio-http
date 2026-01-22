package zio.http

import zio._
import zio.test._

object HandlerAspectSpec extends ZIOSpecDefault {

  // Test context for issue #3141
  case class WebSession(id: Int)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("HandlerAspect")(
      test("HandlerAspect with context can eliminate environment type") {
        val handler0 = handler((_: Request) => ZIO.serviceWith[Int](i => Response.text(i.toString))) @@
          HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, req.headers.size)))
        for {
          response   <- handler0(Request(headers = Headers("accept", "*")))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1")
      },
      // format: off
      test("HandlerAspect with context can eliminate environment type partially") {
        val handlerAspect = HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, req.headers.size)))
        val handler0 = handler { (_: Request) =>
          withContext((_: Boolean, i: Int) => Response.text(i.toString))
          //leftover type is only needed in Scala 2
          //can't be infix because of Scala 3
        }.@@[Boolean](handlerAspect)
        for {
          response   <- ZIO.scoped(handler0(Request(headers = Headers("accept", "*")))).provideEnvironment(ZEnvironment(true))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1")
      },
      test("HandlerAspect with context can eliminate environment type partially while requiring an additional environment") {
        val handlerAspect: HandlerAspect[String, Int] = HandlerAspect.interceptIncomingHandler {
          handler((req: Request) => withContext((s: String) => (req.withBody(Body.fromString(s)), req.headers.size)))
        }
        val handler0: Handler[Boolean with String, Response, Request, Response] = handler { (r: Request) =>
          ZIO.service[Boolean] *> withContext{ (i: Int) =>
            for {
              body <- r.body.asString.orDie
            } yield Response.text(s"$i $body")
          }
          //leftover type is only needed in Scala 2
          //can't be infix because of Scala 3
        }.@@[Boolean](handlerAspect)
        for {
          response   <- ZIO.scoped(handler0(Request(headers = Headers("accept", "*")))).provideEnvironment(ZEnvironment(true) ++ ZEnvironment("test"))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1 test")
      },
      // Issue #3141: Test HandlerAspect with path parameters and withContext at Routes level
      test("HandlerAspect with path parameters and withContext (issue #3141)") {
        val maybeWebSession: HandlerAspect[Any, Option[WebSession]] =
          HandlerAspect.interceptIncomingHandler(
            Handler.fromFunctionZIO[Request] { req =>
              ZIO.succeed((req, Some(WebSession(42))))
            },
          )

        // Routes with path parameters combined with context-providing middleware
        // With the fix, we can now apply @@ directly to the Route
        val route1 = (Method.GET / "base" / string("param") -> handler { (param: String, _: Request) =>
          withContext { (ctx: Option[WebSession]) =>
            ZIO.succeed(Response.text(s"param=$param, session=${ctx.map(_.id).getOrElse(-1)}"))
          }
        }) @@ maybeWebSession

        val route2 = (Method.GET / "multi" / string("a") / int("b") -> handler { (a: String, b: Int, _: Request) =>
          withContext { (ctx: Option[WebSession]) =>
            ZIO.succeed(Response.text(s"a=$a, b=$b, session=${ctx.map(_.id).getOrElse(-1)}"))
          }
        }) @@ maybeWebSession

        val routes: Routes[Any, Response] = Routes(route1, route2)

        for {
          // Test single path param
          response1 <- routes.runZIO(Request.get(URL(Path.root / "base" / "hello")))
          body1     <- response1.body.asString
          // Test multiple path params
          response2 <- routes.runZIO(Request.get(URL(Path.root / "multi" / "test" / "123")))
          body2     <- response2.body.asString
        } yield assertTrue(
          body1 == "param=hello, session=42",
          body2 == "a=test, b=123, session=42",
        )
      },
      // format: on
    )
}
