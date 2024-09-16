package zio.http

import zio._
import zio.http.netty.NettyConfig
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
          response   <- handler0(Request(headers = Headers("accept", "*"))).provideEnvironment(ZEnvironment(true))
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
          response   <- handler0(Request(headers = Headers("accept", "*"))).provideEnvironment(ZEnvironment(true) ++ ZEnvironment("test"))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1 test")
      },

      test("Reproducing class cast exception") {
        //Fails on Handler.scala line 57
        //...
        // val handler: ZIO[Ctx, Response, Response] = self.asInstanceOf[Handler[Ctx, Response, Request, Response]](req)
        //...




        case class WebSession(id: Int)
        val handlerAspect: HandlerAspect[Any, Option[WebSession]] =
          HandlerAspect.interceptIncomingHandler(
            Handler.fromFunctionZIO[Request] { req =>
              ZIO.succeed((req, None))
            }
          )
        val route = Method.GET / "base" / string("1") -> handler((a: String, req: Request) => {
          withContext((c: Option[WebSession]) => {
            ZIO.logInfo("Hello").as(Response.ok)
          })
        }) @@ handlerAspect

        for {
          port <- Server.install(Routes(route))
          client <- ZIO.service[Client]
          url <- ZIO.fromEither(URL.decode(s"http://127.0.0.1:$port/base/1"))
          response <- client.apply(Request.get(url))
        } yield assertTrue(response.status == Status.Ok)
      }.provideSome[TestEnvironment with Scope](
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        Server.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        Client.default
      )
      // format: on
    )
}
