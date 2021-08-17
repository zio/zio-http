package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.HResponse
import zhttp.experiment.internal.{ChannelProxy, HttpMessageAssertion}
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio._
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._

/**
 * Be prepared for some real nasty runtime tests.
 */
object HttpEndpointSpec extends DefaultRunnableSpec with HttpMessageAssertion {
  private val env = EventLoopGroup.auto(1)

  def spec =
    suite("HttpEndpoint")(
      EmptySpec,
      SucceedEmptySpec,
      SucceedOkSpec,
      SucceedFailSpec,
      FailCauseSpec,
      suite("request")(
        CompleteRequestSpec,
        BufferedRequestSpec,
        AnyRequestSpec,
      ),
      UnmatchedPathSpec,
      MatchedPathSpec,
      CombineSpec,
      EchoCompleteResponseSpec,
      EchoStreamingResponseSpec,
    ).provideCustomLayer(env)

  /**
   * Spec for asserting AnyRequest fields and behaviour
   */
  def AnyRequestSpec = {
    suite("succeed(AnyRequest)")(
      testM("status is 200") {
        assertResponse(HttpEndpoint.mount(Http.collect[AnyRequest](_ => HResponse())))(
          isResponse(status(200)),
        )
      },
      testM("status is 500") {
        assertResponse(
          HttpEndpoint.mount(Http.collectM[AnyRequest](_ => ZIO.fail(new Error("SERVER ERROR")))),
        )(
          isResponse(status(500)),
        )
      },
      testM("status is 404") {
        assertResponse(HttpEndpoint.mount(Http.empty.contramap[AnyRequest](i => i)))(
          isResponse(status(404)),
        )
      },
      testM("status is 200") {
        assertResponse(HttpEndpoint.mount(Http.collectM[AnyRequest](_ => UIO(HResponse()))))(
          isResponse(status(200)),
        )
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
        assertBufferedRequest(header = header.set("H1", "K1"))(
          isRequest(header(Header("H1", "K1"))),
        )
      },
    )
  }

  /**
   * Spec for asserting BufferedRequest fields and behaviour
   */

  def BufferedRequestSpec = {
    suite("succeed(Buffered)")(
      testM("status is 200") {
        assertResponse(HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => HResponse())))(
          isResponse(status(200)),
        )
      },
      testM("status is 500") {
        assertResponse(
          HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](_ => ZIO.fail(new Error("SERVER ERROR")))),
        )(
          isResponse(status(500)),
        )
      },
      testM("status is 404") {
        assertResponse(HttpEndpoint.mount(Http.empty.contramap[BufferedRequest[ByteBuf]](i => i)))(
          isResponse(status(404)),
        )
      },
      testM("status is 200") {
        assertResponse(HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](_ => UIO(HResponse()))))(
          isResponse(status(200)),
        )
      },
      testM("req.content is 'ABCDE'") {
        assertBufferedRequestContent(content = List("A", "B", "C", "D", "E"))(equalTo(List("A", "B", "C", "D", "E")))
      } @@ nonFlaky,
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
        assertBufferedRequest(header = header.set("H1", "K1"))(
          isRequest(header(Header("H1", "K1"))),
        )
      },
    )
  }

  /**
   * Spec for asserting CompleteRequest fields and behaviour
   */

  def CompleteRequestSpec = {
    suite("succeed(CompleteRequest)")(
      testM("status is 200") {
        assertResponse(HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => HResponse())))(
          isResponse(status(200)),
        )
      },
      testM("status is 500") {
        assertResponse(
          HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](_ => ZIO.fail(new Error("SERVER ERROR")))),
        )(
          isResponse(status(500)),
        )
      },
      testM("status is 404") {
        assertResponse(HttpEndpoint.mount(Http.empty.contramap[CompleteRequest[ByteBuf]](i => i)))(
          isResponse(status(404)),
        )
      },
      testM("req.content is 'ABCD'") {
        assertCompleteRequest(content = List("A", "B", "C", "D"))(
          isCompleteRequest(requestBody("ABCD")),
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
        assertCompleteRequest(header = header.set("H1", "K1"))(
          isRequest(header(Header("H1", "K1"))),
        )
      },
    )
  }

  /**
   * Spec for asserting behaviour of an failing endpoint
   */
  def FailCauseSpec = {
    suite("fail(cause)")(
      testM("status is 500") {
        assertResponse(HttpEndpoint.fail(new Error("SERVER_ERROR")))(isResponse(status(500)))
      },
      testM("content is SERVER_ERROR") {
        assertResponse(HttpEndpoint.fail(new Error("SERVER_ERROR")))(isResponse(isContent(hasBody("SERVER_ERROR"))))
      },
      testM("headers are set") {
        assertResponse(HttpEndpoint.fail(new Error("SERVER_ERROR")))(isResponse(header("content-length")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeed with a failing Http
   */
  def SucceedFailSpec = {
    suite("succeed(fail)")(
      testM("status is 500") {
        assertResponse(HttpEndpoint.mount(Http.fail(new Error("SERVER_ERROR"))))(isResponse(status(500)))
      },
      testM("content is SERVER_ERROR") {
        assertResponse(HttpEndpoint.mount(Http.fail(new Error("SERVER_ERROR"))))(
          isResponse(isContent(hasBody("SERVER_ERROR"))),
        )
      },
      testM("headers are set") {
        assertResponse(HttpEndpoint.mount(Http.fail(new Error("SERVER_ERROR"))))(isResponse(header("content-length")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeeds with a succeeding Http
   */
  def SucceedOkSpec = {
    suite("succeed(ok)")(
      testM("status is 200") {
        assertResponse(HttpEndpoint.mount(Http.succeed(HResponse())))(isResponse(status(200)))
      },
      suite("POST")(
        testM("status is 200") {
          assertResponse(
            HttpEndpoint.mount(Http.succeed(HResponse())),
            method = HttpMethod.POST,
            content = List("A", "B", "C"),
          )(
            isResponse(status(200)),
          )
        },
      ),
      testM("headers are empty") {
        assertResponse(HttpEndpoint.mount(Http.succeed(HResponse())))(isResponse(noHeader))
      },
      testM("headers are set") {
        assertResponse(HttpEndpoint.mount(Http.succeed(HResponse(headers = List(Header("key", "value"))))))(
          isResponse(header("key", "value")),
        )
      },
      testM("version is 1.1") {
        assertResponse(HttpEndpoint.mount(Http.succeed(HResponse())))(isResponse(version("HTTP/1.1")))
      },
      testM("version is 1.1") {
        assertResponse(HttpEndpoint.mount(Http.succeed(HResponse())))(isResponse(version("HTTP/1.1")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeeds with an empty Http
   */
  def SucceedEmptySpec = {
    suite("succeed(empty)")(
      testM("status is 404") {
        assertResponse(HttpEndpoint.empty)(isResponse(status(404)))
      },
      testM("headers are empty") {
        assertResponse(HttpEndpoint.empty)(isResponse(noHeader))
      },
      testM("version is 1.1") {
        assertResponse(HttpEndpoint.empty)(isResponse(version("HTTP/1.1")))
      },
      testM("version is 1.1") {
        assertResponse(HttpEndpoint.empty)(isResponse(version("HTTP/1.1")))
      },
    )
  }

  /**
   * Spec for an Endpoint that is empty
   */
  def EmptySpec = {
    suite("empty")(
      suite("GET")(
        testM("status is 404") {
          assertResponse(HttpEndpoint.empty)(isResponse(status(404)))
        },
        testM("headers are empty") {
          assertResponse(HttpEndpoint.empty)(isResponse(noHeader))
        },
        testM("version is 1.1") {
          assertResponse(HttpEndpoint.empty)(isResponse(version("HTTP/1.1")))
        },
        testM("version is 1.1") {
          assertResponse(HttpEndpoint.empty)(isResponse(version("HTTP/1.1")))
        },
      ),
      suite("POST")(
        testM("status is 404") {
          assertResponse(HttpEndpoint.empty, method = HttpMethod.POST, content = List("A", "B", "C"))(
            isResponse(status(404)),
          )
        },
      ),
    )
  }

  /**
   * Spec for combining multiple Endpoints that succeed
   */
  def CombineSpec = {
    suite("orElse")(
      testM("status is 200") {
        val app = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.OK))) <>
          HttpEndpoint.mount(Root / "b")(Http.succeed(HResponse(status = Status.CREATED)))

        assertResponse(url = "/a", app = app)(isResponse(status(200)))
      },
      testM("matches first") {
        val app = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.OK))) <>
          HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.CREATED)))

        assertResponse(url = "/a", app = app)(isResponse(status(200)))
      },
      testM("status is 404") {
        val app = HttpEndpoint.mount(Root / "a")(Http.succeed(HResponse(status = Status.OK))) <>
          HttpEndpoint.mount(Root / "b")(Http.succeed(HResponse(status = Status.CREATED)))

        assertResponse(url = "/c", app = app)(isResponse(status(404)))
      },
    )
  }

  /**
   * Spec to handle cases when no endpoint matches
   */
  def UnmatchedPathSpec = {
    suite("unmatched path /abc")(
      testM("type AnyRequest") {
        assertResponse(HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => HResponse())))(
          isResponse(status(404)),
        )
      },
      testM("type BufferedRequest") {
        assertResponse(HttpEndpoint.mount(Root / "abc")(Http.collect[BufferedRequest[ByteBuf]](_ => HResponse())))(
          isResponse(status(404)),
        )
      },
      testM("type CompleteRequest") {
        assertResponse(HttpEndpoint.mount(Root / "abc")(Http.collect[CompleteRequest[ByteBuf]](_ => HResponse())))(
          isResponse(status(404)),
        )
      },
      testM("type Any") {
        assertResponse(HttpEndpoint.mount(Root / "abc")(Http.succeed(HResponse())))(
          isResponse(status(404)),
        )
      },
    )
  }

  /**
   * Spec to handle cases when endpoint matches
   */
  def MatchedPathSpec = {
    suite("matched path")(
      testM("exact match") {
        assertResponse(
          url = "/abc",
          app = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => HResponse())),
        )(
          isResponse(status(200)),
        )
      },
      testM("starts with match") {
        assertResponse(
          url = "/abc/p",
          app = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => HResponse())),
        )(
          isResponse(status(200)),
        )
      },
      testM("does not match") {
        assertResponse(
          url = "/abcd",
          app = HttpEndpoint.mount(Root / "abc")(Http.collect[AnyRequest](_ => HResponse())),
        )(
          isResponse(status(404)),
        )
      },
    )
  }

  def echoComplete(req: BufferedRequest[ByteBuf]): ZIO[Any, Nothing, HResponse[Any, Nothing, ByteBuf]] =
    for {
      content <- req.content.runCollect.map(chunk => Unpooled.copiedBuffer(chunk.toArray: _*))
    } yield HResponse(content = HContent.complete(content))

  def echoComplete(req: CompleteRequest[ByteBuf]): ZIO[Any, Nothing, HResponse[Any, Nothing, ByteBuf]] =
    UIO(HResponse(content = HContent.complete(req.content)))

  def EchoCompleteResponseSpec = {
    suite("CompleteResponse")(
      suite("CompleteRequest")(
        testM("status is 200") {
          for {
            proxy <- ChannelProxy.make(HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](echoComplete(_))))
            _     <- proxy.request()
            _     <- proxy.end
            res   <- proxy.receive
          } yield assert(res)(isResponse(status(200)))
        },
        testM("has Content") {
          for {
            proxy <- ChannelProxy.make(HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](echoComplete(_))))
            _     <- proxy.request()
            _     <- proxy.end
            _     <- proxy.receive
            data  <- proxy.receive
          } yield assert(data)(isContent)
        },
        testM("content is 'ABCD'") {
          for {
            proxy <- ChannelProxy.make(HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](echoComplete(_))))
            _     <- proxy.request()
            _     <- proxy.end("A", "B", "C", "D")
            _     <- proxy.receive
            data  <- proxy.receive
          } yield assert(data)(isLastContent(body("ABCD")))
        } @@ nonFlaky,
      ),
      suite("BufferedRequest")(
        testM("status is 200") {
          for {
            proxy <- ChannelProxy.make(HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](echoComplete(_))))
            _     <- proxy.request()
            _     <- proxy.end("A", "B", "C", "D")
            res   <- proxy.receive
          } yield assert(res)(isResponse(status(200)))
        },
        testM("has Content") {
          for {
            proxy <- ChannelProxy.make(HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](echoComplete(_))))
            _     <- proxy.request()
            _     <- proxy.end("A", "B", "C", "D")
            _     <- proxy.receive
            data  <- proxy.receive
          } yield assert(data)(isContent)
        },
        testM("content is 'ABCD'") {
          for {
            proxy <- ChannelProxy.make(HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](echoComplete(_))))
            _     <- proxy.request()
            _     <- proxy.end("A", "B", "C", "D")
            _     <- proxy.receive
            data  <- proxy.receive
          } yield assert(data)(isLastContent(body("ABCD")))
        } @@ nonFlaky,
      ) @@ timeout(10 seconds),
    )
  }

  def EchoStreamingResponseSpec = {
    val streamingResponse = HResponse(content =
      HContent.fromStream(
        ZStream
          .fromIterable(List("A", "B", "C", "D"))
          .map(text => Unpooled.copiedBuffer(text.getBytes)),
      ),
    )

    suite("StreamingResponse")(
      suite("CompleteRequest")(
        testM("status is 200") {
          for {
            proxy <- ChannelProxy.make(
              HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => streamingResponse)),
            )
            _     <- proxy.request()
            _     <- proxy.end
            res   <- proxy.receive
          } yield assert(res)(isResponse(status(200)))
        },
        testM("has Content") {
          for {
            proxy <- ChannelProxy.make(
              HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => streamingResponse)),
            )
            _     <- proxy.request()
            _     <- proxy.end
            _     <- proxy.receive
            data  <- proxy.receive
          } yield assert(data)(isContent)
        },
        testM("content is 'ABCD'") {
          for {
            proxy <- ChannelProxy.make(
              HttpEndpoint.mount(Http.collect[CompleteRequest[ByteBuf]](_ => streamingResponse)),
            )
            _     <- proxy.request()
            _     <- proxy.end("A", "B", "C", "D")
            _     <- proxy.receive
            bytes <- proxy.receiveN(4).map(_.asInstanceOf[List[HttpContent]])
            str = bytes.map(_.content.toString(HTTP_CHARSET)).mkString("")
          } yield assert(str)(equalTo("ABCD"))
        } @@ nonFlaky,
      ),
      suite("BufferedRequest")(
        testM("status is 200") {
          for {
            proxy <- ChannelProxy.make(
              HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => streamingResponse)),
            )
            _     <- proxy.request()
            _     <- proxy.end
            res   <- proxy.receive
          } yield assert(res)(isResponse(status(200)))
        },
        testM("has Content") {
          for {
            proxy <- ChannelProxy.make(
              HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => streamingResponse)),
            )
            _     <- proxy.request()
            _     <- proxy.end
            _     <- proxy.receive
            data  <- proxy.receive
          } yield assert(data)(isContent)
        },
        testM("content is 'ABCD'") {
          for {
            proxy <- ChannelProxy.make(
              HttpEndpoint.mount(Http.collect[BufferedRequest[ByteBuf]](_ => streamingResponse)),
            )
            _     <- proxy.request()
            _     <- proxy.end("A", "B", "C", "D")
            _     <- proxy.receive
            bytes <- proxy.receiveN(4).map(_.asInstanceOf[List[HttpContent]])
            str = bytes.map(_.content.toString(HTTP_CHARSET)).mkString("")
          } yield assert(str)(equalTo("ABCD"))
        } @@ nonFlaky,
      ),
    )
  }
}
