package zio.http

import zio._
import zio.test._

object HandlerAspectSpec extends ZIOSpecDefault {
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
      // format: on
      test("HandlerAspect with context works on a route with path parameters (#3141)") {
        case class WebSession(id: Int)
        val maybeWebSession: HandlerAspect[Any, Option[WebSession]] =
          HandlerAspect.interceptIncomingHandler(
            Handler.fromFunctionZIO[Request](req => ZIO.succeed((req, Option(WebSession(1))))),
          )
        val route = Method.GET / "base" / string("id") -> (handler { (id: String, req: Request) =>
          withContext((session: Option[WebSession]) =>
            ZIO.succeed(Response.text(s"$id:${session.map(_.id).getOrElse(-1)}:${req.method.name}")),
          )
        } @@ maybeWebSession)
        for {
          response   <- ZIO.scoped(route.toHandler.runZIO(Request.get(URL.root / "base" / "42")))
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, bodyString == "42:1:GET")
      },
      test("HandlerAspect without context works on a route with path parameters (#3141)") {
        val aspect = HandlerAspect.interceptIncomingHandler(
          Handler.fromFunction[Request](req => (req, ())),
        )
        val route  =
          Method.GET / "base" / string("id") / int("num") -> (handler { (id: String, num: Int, req: Request) =>
            Response.text(s"$id:$num:${req.method.name}")
          } @@ aspect)
        for {
          response   <- ZIO.scoped(route.toHandler.runZIO(Request.get(URL.root / "base" / "42" / "7")))
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, bodyString == "42:7:GET")
      },
    )
}
