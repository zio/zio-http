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
          ZIO.service[Boolean] *> ZIO.serviceWith[Int](i => Response.text(i.toString))
          //leftover type is only needed in Scala 2
          //can't be infix because of Scala 3
        }.@@[Boolean](handlerAspect)
        for {
          response   <- handler0(Request(headers = Headers("accept", "*"))).provideEnvironment(ZEnvironment(true))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1")
      },
      test("HandlerAspect with context can eliminate environment type partially while requiring an additional environment") {
        val handlerAspect: HandlerAspect[String, Int] = HandlerAspect.interceptIncomingHandler {
          handler((req: Request) => ZIO.serviceWith[String](s => (req.withBody(Body.fromString(s)), req.headers.size)))
        }
        val handler0: Handler[Boolean with String, Response, Request, Response] = handler { (r: Request) =>
          ZIO.service[Boolean] *> ZIO.serviceWithZIO[Int] { i =>
            for {
              body <- r.body.asString.orDie
            } yield Response.text(s"$i $body")
          }
          //leftover type is only needed in Scala 2
          //can't be infix because of Scala 3
        }.@@[Boolean](handlerAspect)
        for {
          response   <- handler0(Request(headers = Headers("accept", "*"))).provideEnvironment(ZEnvironment(true) ++ ZEnvironment("test"))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1 test")
      },
      // format: on
    )
}
