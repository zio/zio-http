package zio.http

import scala.util.chaining.scalaUtilChainingOps

import zio._
import zio.test._

object ForwardHeaderSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ForwardHeaderSpec")(
      suite("integration")(
        test("forward headers by header type") {
          val routes = Routes(
            Method.GET / "get"   -> handler((_: Request) =>
              for {
                client   <- ZIO.service[Client]
                response <- (client @@ ZClientAspect.forwardHeaders)
                  .batched(Request.post(url"http://localhost:8080/post", Body.empty))
              } yield response,
            ),
            Method.POST / "post" -> handler((req: Request) => Response.ok.addHeader(req.header(Header.Accept).get)),
          ).sandbox @@ Middleware.forwardHeaders(Header.Accept)

          for {
            _        <- Server.installRoutes(routes)
            response <- Client.batched(
              Request.get(url"http://localhost:8080/get").addHeader(Header.Accept(MediaType.application.json)),
            )
          } yield assertTrue(response.headers(Header.Accept).contains(Header.Accept(MediaType.application.json)))
        },
        test("forward by mapping headers") {
          val routes = Routes(
            Method.GET / "get2"   -> handler((_: Request) =>
              for {
                client   <- ZIO.service[Client]
                response <- (client @@ ZClientAspect.forwardHeaders)
                  .batched(Request.post(url"http://localhost:8080/post", Body.empty))
              } yield response,
            ),
            Method.POST / "post2" -> handler((req: Request) => Response.ok.addHeader(req.header(Header.Accept).get)),
          ).sandbox @@ Middleware.forwardHeaders((headers: Headers) =>
            Headers.fromIterable(headers.filter(_.headerName == Header.Accept.name)),
          )

          for {
            _        <- Server.installRoutes(routes)
            response <- Client.batched(
              Request.get(url"http://localhost:8080/get2").addHeader(Header.Accept(MediaType.application.json)),
            )
          } yield assertTrue(response.headers(Header.Accept).contains(Header.Accept(MediaType.application.json)))
        },
      ).provideShared(Client.default, Server.default) @@ TestAspect.withLiveClock @@ TestAspect.flaky,
      test("forwardHeaders(f) does not write to stdout") {
        val baos        = new java.io.ByteArrayOutputStream()
        val originalOut = java.lang.System.out
        val routes      = Routes(
          Method.GET / "test" -> handler(Response.ok),
        ).sandbox @@ Middleware.forwardHeaders((h: Headers) => h)

        for {
          _ <- ZIO.attempt(java.lang.System.setOut(new java.io.PrintStream(baos)))
          response <- routes
            .runZIO(Request.get(URL.root / "test").addHeader(Header.Accept(MediaType.application.json)))
            .ensuring(ZIO.attempt(java.lang.System.setOut(originalOut)).ignoreLogged)
          stdout = baos.toString
        } yield assertTrue(response.status == Status.Ok, stdout.isEmpty)
      },
    )

}
