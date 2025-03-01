package zio.http.security

import zio._
import zio.test._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.netty.NettyConfig
import zio.http.template.Dom

object UserDataSpec extends ZIOSpecDefault {
  /*
      - DOM-based XSS vulnerabilities are client-side and thus difficult to prevent. Any client-side vulnerable code could be sent by the server.
      Some clients implement escaping before sending requests and ZIO-HTTP could make some tools to make safer the use of a client.

      - HTML server-side XSS is covered unless using `DOM.raw`. I would opt to indicate that using `DOM.raw` might be unsafe.

   */

  val tuples = Gen.fromIterable(
    List(
      (MediaType.text.`html`, "<script>alert('XSS');</script>", "&lt;script&gt;alert(&#x27;XSS&#x27;);&lt;/script&gt;"),
      (MediaType.text.`html`, "&", "&amp;"),
      (MediaType.text.`html`, "<", "&lt;"),
      (MediaType.text.`html`, ">", "&gt;"),
      (MediaType.text.`html`, "\"", "&quot;"),
      (MediaType.text.`html`, "'", "&#x27;"),
    ),
  )

  val functions = Gen.fromIterable(
    List(
      (s: String) => Dom.element("html", Dom.text(s)),
      (s: String) => Dom.element("div", Dom.attr("div", s)),
    ),
  )

  val spec = suite("UserDataSpec")(
    test("No sanitation and write to server") {
      val endpoint = Endpoint(Method.GET / "test")
        .query(HttpCodec.query[String]("data"))
        .out[String]
      val route    = endpoint.implementHandler(Handler.fromFunction { (s: String) =>
        // writeToServer or other actions
        s
      })
      // this is not a bug but could be a vulnerability used wrong
      check(tuples.zip(functions)) { case (_, msg, expectedResponse, _) =>
        val request =
          Request.get(URL(Path.root / "test", queryParams = QueryParams(("data", msg))))
        for {
          response <- route.toRoutes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body.contains(expectedResponse))
      }
    } @@ TestAspect.failing,
    test("No sanitation using Dom") {
      val endpoint = Endpoint(Method.GET / "test")
        .in[Dom]
        .out[Dom]
      val route    = endpoint.implementHandler(Handler.fromFunction(identity))
      // this is not a bug but could be a vulnerability used wrong
      check(tuples.zip(functions)) { case (_, msg, expectedResponse, _) =>
        val request =
          Request.post(URL(Path.root / "test"), Body.fromString(msg))
        for {
          response <- route.toRoutes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body.contains(expectedResponse))
      }
    } @@ TestAspect.failing,
    test("Header injection") {
      check(tuples.zip(functions).zip(Gen.alphaNumericStringBounded(1, 50))) {
        case (mediaType, msg, expectedResponse, f, suffix) =>
          val endpoint = Endpoint(Method.GET / "test" / suffix)
            .query(HttpCodec.query[String]("data"))
            .out[Dom]
          val route    = endpoint.implementHandler(Handler.fromFunction { (s: String) => f(s) })
          val request  =
            Request
              .get(URL(Path.root / "test" / suffix, queryParams = QueryParams(("data", msg))))
              .addHeader(Header.Accept(mediaType))
          for {
            response <- route.toRoutes.runZIO(request)
            body     <- response.body.asString
          } yield assertTrue(body.contains(expectedResponse))
      }
    },
    test("Header injection DOM") {
      val endpoint = Endpoint(Method.GET / "test")
        .query(HttpCodec.query[Dom]("data"))
        .out[Dom]
      val route    = endpoint.implementHandler(Handler.fromFunction(s => s))
      check(tuples.zip(functions)) { case (mediaType, msg, expectedResponse, _) =>
        val request =
          Request
            .get(URL(Path.root / "test", queryParams = QueryParams(("data", msg))))
            .addHeader(Header.Accept(mediaType))
        for {
          response <- route.toRoutes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(body.contains(expectedResponse))
      }
    } @@ TestAspect.failing,
    test("Path injection") {
      check(tuples.zip(functions).zip(Gen.alphaNumericStringBounded(1, 50))) {
        case (mediaType, msg, expectedResponse, f, suffix) =>
          val request = Request.get(URL(Path.root / "test" / suffix / msg)).addHeader(Header.Accept(mediaType))
          val route   = Routes(
            Endpoint(Method.GET / "test" / suffix / string("message"))
              .out[Dom]
              .implementHandler(Handler.fromFunction { (s: String) =>
                f(s)
              }),
          )
          for {
            response <- route.runZIO(request)
            body     <- response.body.asString
          } yield assertTrue(body.contains(expectedResponse))
      }
    },
    test("Body injection") {
      check(tuples.zip(functions).zip(Gen.alphaNumericStringBounded(1, 50))) {
        case (mediaType, msg, expectedResponse, f, suffix) =>
          val body    = Body.fromArray(msg.getBytes())
          val request = Request.post(url"/test/$suffix", body).addHeader(Header.Accept(mediaType))
          val route   = Routes(Method.POST / "test" / suffix -> handler { (req: Request) =>
            for {
              msg <- req.body.asString.orDie
            } yield Response.text(f(msg).encode)
          })
          for {
            response <- route.runZIO(request)
            body     <- response.body.asString
          } yield assertTrue(body.contains(expectedResponse))
      }
    },
    test("Error injection") {
      val routes = Routes(Method.POST / "test" -> handler { (req: Request) =>
        req.body.asString.orDie.map(msg => Response.error(Status.InternalServerError, msg))
      })
      for {
        port   <- Server.install(routes)
        result <- check(tuples.zip(functions)) { case (mediaType, msg, expectedResponse, _) =>

          val body    = Body.fromString(msg)
          val request = Request.post("/test", body).addHeader(Header.Accept(mediaType))
          for {
            response <- ZIO.scoped {
              Client
                .batched(request.updateURL(_ => URL.decode(s"http://localhost:$port/test").toOption.get))
            }
            body     <- response.body.asString
          } yield assertTrue(body == expectedResponse)
        }
      } yield result
    },
  ).provide(
    Server.customized,
    ZLayer.succeed(
      Server.Config.default,
    ),
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
    Client.default,
  ) @@ TestAspect.sequential

}
