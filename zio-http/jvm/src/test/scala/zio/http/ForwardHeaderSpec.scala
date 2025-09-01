package zio.http

import scala.util.chaining.scalaUtilChainingOps

import zio._
import zio.test._

object ForwardHeaderSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ForwardHeaderSpec")(
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
    ).provideShared(Client.default, Server.default) @@ TestAspect.withLiveClock

}
