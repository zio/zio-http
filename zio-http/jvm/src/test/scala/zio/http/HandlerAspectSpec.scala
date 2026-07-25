package zio.http

import zio._
import zio.test.Assertion._
import zio.test._

object HandlerAspectSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("HandlerAspect")(
      test("applying an aspect to a handler with path parameters does not compile") {
        // Regression test for #3141.
        //
        // `Handler.@@` casts the handler's input to `Request`. On a route with
        // path parameters that input is a tuple such as `(String, Request)`,
        // and the cast fails at runtime with:
        //
        //   java.lang.ClassCastException: class zio.http.Request cannot be
        //   cast to class scala.Tuple2
        //
        // The old guard asked for `IsRequest[In1]`. Being contravariant,
        // `IsRequest[Request]` also conformed to `IsRequest[Nothing]`, and
        // since `In1` is only bounded by `In1 <: In` the compiler could
        // satisfy it by inferring `In1 = Nothing` — so the guard never saw
        // the tuple. Constraining `In` itself with `Request <:< In` leaves
        // nothing to infer around.
        val result = typeCheck {
          """import zio.http._
             import zio._

             val aspect: HandlerAspect[Any, Option[Int]] =
               HandlerAspect.interceptIncomingHandler(
                 Handler.fromFunctionZIO[Request](req => ZIO.succeed((req, None)))
               )

             Method.GET / "base" / string("p") -> handler { (_: String, _: Request) =>
               withContext((_: Option[Int]) => ZIO.succeed(Response.ok))
             } @@ aspect
          """
        }
        assertZIO(result)(isLeft)
      },
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
    )
}
