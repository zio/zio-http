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
      test("HandlerAspect with context works for routes with a path parameter") {
        case class WebSession(id: Int)

        val sessionAspect: HandlerAspect[Any, Option[WebSession]] =
          HandlerAspect.interceptIncomingHandler(
            handler((req: Request) => (req, Some(WebSession(7)))),
          )

        val route =
          Method.GET / "base" / string("id") -> (handler((id: String, _: Request) =>
            withContext((session: Option[WebSession]) => Response.text(s"$id:${session.map(_.id)}")),
          ) @@ sessionAspect)

        for {
          response   <- route.toRoutes.runZIO(Request.get(URL(Path.root / "base" / "alpha")))
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, bodyString == "alpha:Some(7)")
      },
      test("HandlerAspect with context works for routes with multiple path parameters") {
        val authAspect: HandlerAspect[Any, Int] =
          HandlerAspect.interceptIncomingHandler(
            handler((req: Request) => (req, 7)),
          )

        val route =
          Method.DELETE / string("message") / int("id") -> (handler((message: String, id: Int, _: Request) =>
            withContext((auth: Int) => Response.text(s"$message $id $auth")),
          ) @@ authAspect)

        for {
          response   <- route.toRoutes.runZIO(Request.delete(URL(Path.root / "twenty" / "3")))
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, bodyString == "twenty 3 7")
      },
      // format: on
    )
}
