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
      test("HandlerAspect preserves path parameters and context (#3141)") {
        val aspect = HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, 7)))
        val routes = Routes(
          Method.GET / "base" / string("1") -> handler { (a: String, _: Request) =>
            withContext((i: Int) => Response.text(s"$a $i"))
          } @@ aspect,
        )
        for {
          response <- routes.runZIO(Request.get(url"/base/hello"))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, body == "hello 7")
      },
      test("HandlerAspect preserves multiple path parameters and request updates") {
        val aspect = HandlerAspect.updateRequest(_.withBody(Body.fromString("updated"))) ++
          HandlerAspect.updateResponse(_.status(Status.Accepted))
        val routes = Routes(
          Method.GET / string("name") / int("age") -> handler { (name: String, age: Int, req: Request) =>
            req.body.asString.orDie.map(body => Response.text(s"$name $age $body"))
          } @@ aspect,
        )
        for {
          response <- routes.runZIO(Request.get(url"/Alice/42"))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Accepted, body == "Alice 42 updated")
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
    )
}
