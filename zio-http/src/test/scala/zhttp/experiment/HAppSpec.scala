package zhttp.experiment

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.HResponse
import zhttp.experiment.internal.HttpMessageAssertion
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio._
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._

/**
 * Be prepared for some real nasty runtime tests.
 */
object HAppSpec extends DefaultRunnableSpec with HttpMessageAssertion {
  private val env = EventLoopGroup.auto(1)

  def spec =
    suite("HApp")(
      suite("empty")(
        suite("GET")(
          testM("status is 404") {
            assertResponse(HApp.empty)(isResponse(status(404)))
          },
          testM("headers are empty") {
            assertResponse(HApp.empty)(isResponse(noHeader))
          },
          testM("version is 1.1") {
            assertResponse(HApp.empty)(isResponse(version("HTTP/1.1")))
          },
          testM("version is 1.1") {
            assertResponse(HApp.empty)(isResponse(version("HTTP/1.1")))
          },
        ),
        suite("POST")(
          testM("status is 404") {
            assertResponse(HApp.empty, method = HttpMethod.POST, content = List("A", "B", "C"))(isResponse(status(404)))
          },
        ),
      ),
      suite("succeed(empty)")(
        testM("status is 404") {
          assertResponse(HApp.empty)(isResponse(status(404)))
        },
        testM("headers are empty") {
          assertResponse(HApp.empty)(isResponse(noHeader))
        },
        testM("version is 1.1") {
          assertResponse(HApp.empty)(isResponse(version("HTTP/1.1")))
        },
        testM("version is 1.1") {
          assertResponse(HApp.empty)(isResponse(version("HTTP/1.1")))
        },
      ),
      suite("succeed(ok)")(
        testM("status is 200") {
          assertResponse(HApp.from(Http.succeed(HResponse())))(isResponse(status(200)))
        },
        suite("POST")(
          testM("status is 200") {
            assertResponse(
              HApp.from(Http.succeed(HResponse())),
              method = HttpMethod.POST,
              content = List("A", "B", "C"),
            )(
              isResponse(status(200)),
            )
          },
        ),
        testM("headers are empty") {
          assertResponse(HApp.from(Http.succeed(HResponse())))(isResponse(noHeader))
        },
        testM("headers are set") {
          assertResponse(HApp.from(Http.succeed(HResponse(headers = List(Header("key", "value"))))))(
            isResponse(header("key", "value")),
          )
        },
        testM("version is 1.1") {
          assertResponse(HApp.from(Http.succeed(HResponse())))(isResponse(version("HTTP/1.1")))
        },
        testM("version is 1.1") {
          assertResponse(HApp.from(Http.succeed(HResponse())))(isResponse(version("HTTP/1.1")))
        },
      ),
      suite("succeed(fail)")(
        testM("status is 500") {
          assertResponse(HApp.from(Http.fail(new Error("SERVER_ERROR"))))(isResponse(status(500)))
        },
        testM("content is SERVER_ERROR") {
          assertResponse(HApp.from(Http.fail(new Error("SERVER_ERROR"))))(
            isResponse(isContent(bodyText("SERVER_ERROR"))),
          )
        },
        testM("headers are set") {
          assertResponse(HApp.from(Http.fail(new Error("SERVER_ERROR"))))(isResponse(header("content-length")))
        },
      ),
      suite("fail(cause)")(
        testM("status is 500") {
          assertResponse(HApp.fail(new Error("SERVER_ERROR")))(isResponse(status(500)))
        },
        testM("content is SERVER_ERROR") {
          assertResponse(HApp.fail(new Error("SERVER_ERROR")))(isResponse(isContent(bodyText("SERVER_ERROR"))))
        },
        testM("headers are set") {
          assertResponse(HApp.fail(new Error("SERVER_ERROR")))(isResponse(header("content-length")))
        },
      ),
      suite("request")(
        suite("succeed(CompleteRequest)")(
          testM("status is 200") {
            assertResponse(HApp.from(Http.collect[CompleteRequest[ByteBuf]](_ => HResponse())))(isResponse(status(200)))
          },
          testM("status is 500") {
            assertResponse(
              HApp.from(Http.collectM[CompleteRequest[ByteBuf]](_ => ZIO.fail(new Error("SERVER ERROR")))),
            )(
              isResponse(status(500)),
            )
          },
          testM("status is 404") {
            assertResponse(HApp.from(Http.empty.contramap[CompleteRequest[ByteBuf]](i => i)))(
              isResponse(status(404)),
            )
          },
          testM("req.content is 'ABCD'") {
            assertCompleteRequest(content = List("A", "B", "C", "D"))(
              isCompleteRequest(content("ABCD")),
            )
          },
          testM("req.url is '/abc'") {
            assertCompleteRequest("/abc", HttpMethod.GET)(isRequest(url("/abc")))
          },
          testM("req.method is 'GET'") {
            assertCompleteRequest(method = HttpMethod.GET)(isRequest(method(Method.GET)))
          },
          testM("req.method is 'POST'") {
            assertCompleteRequest(method = HttpMethod.POST)(isRequest(method(Method.POST)))
          },
          testM("req.header is 'H1: K1'") {
            assertCompleteRequest(header = new DefaultHttpHeaders().set("H1", "K1"))(
              isRequest(header(Header("H1", "K1"))),
            )
          },
        ),
        suite("succeed(Buffered)")(
          testM("status is 200") {
            assertResponse(HApp.from(Http.collect[BufferedRequest[ByteBuf]](_ => HResponse())))(isResponse(status(200)))
          },
          testM("status is 500") {
            assertResponse(
              HApp.from(Http.collectM[BufferedRequest[ByteBuf]](_ => ZIO.fail(new Error("SERVER ERROR")))),
            )(
              isResponse(status(500)),
            )
          },
          testM("status is 404") {
            assertResponse(HApp.from(Http.empty.contramap[BufferedRequest[ByteBuf]](i => i)))(
              isResponse(status(404)),
            )
          },
          testM("status is 200") {
            assertResponse(HApp.from(Http.collectM[BufferedRequest[ByteBuf]](_ => UIO(HResponse()))))(
              isResponse(status(200)),
            )
          },
          testM("req.content is 'ABCDE'") {
            for {
              promise <- Promise.make[Nothing, Chunk[String]]
              proxy   <- HApp.from {
                Http.collectM[BufferedRequest[ByteBuf]] { req =>
                  req.content
                    .map(_.toString(HTTP_CHARSET))
                    .runCollect
                    .tap(promise.succeed)
                    .as(HResponse())
                }
              }.proxy

              _   <- proxy.request("/", HttpMethod.POST)
              _   <- proxy.data(List("A", "B", "C", "D", "E"))
              req <- promise.await.map(_.toList)

            } yield assert(req)(equalTo(List("A", "B", "C", "D", "E")))
          },
          testM("req.url is '/abc'") {
            assertBufferedRequest("/abc", HttpMethod.GET)(isRequest(url("/abc")))
          },
          testM("req.method is 'GET'") {
            assertBufferedRequest(method = HttpMethod.GET)(isRequest(method(Method.GET)))
          },
          testM("req.method is 'POST'") {
            assertBufferedRequest(method = HttpMethod.POST)(isRequest(method(Method.POST)))
          },
          testM("req.header is 'H1: K1'") {
            assertBufferedRequest(header = new DefaultHttpHeaders().set("H1", "K1"))(
              isRequest(header(Header("H1", "K1"))),
            )
          },
        ) @@ nonFlaky(1000),
      ),
    ).provideCustomLayer(env) @@ timeout(120 second)
}
