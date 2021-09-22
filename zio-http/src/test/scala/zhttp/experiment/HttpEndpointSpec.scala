package zhttp.experiment

import io.netty.handler.codec.http._
import zhttp.experiment.HttpEndpoint.InvalidMessage
import zhttp.experiment.HttpMessage._
import zhttp.experiment.internal.{EndpointClient, HttpMessageAssertions}
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio._
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion.{equalTo, isLeft, isNone}
import zio.test.TestAspect._
import zio.test._

import java.net.InetAddress

/**
 * Be prepared for some real nasty runtime tests.
 */
object HttpEndpointSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env                           = EventLoopGroup.auto(1)
  private val Ok: AnyResponse[Any, Nothing] = AnyResponse()

  def spec =
    suite("HttpEndpoint")(
      EmptySpec,
      OkSpec,
      FailSpec,
      RequestSpec,
      EchoStreamingResponseSpec,
      IllegalMessageSpec,
      ContentDecoderSpec,
    ).provideCustomLayer(env) @@ timeout(10 seconds)

  /**
   * Spec for asserting Request fields and behavior
   */
  def RequestSpec = {
    suite("succeed(Request)")(
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collect[Request](_ => Ok)).getResponse
        assertM(res)(isResponse(responseStatus(200)))
      },
      testM("status is 500") {
        val res = HttpEndpoint.mount(Http.collectM[Request](_ => ZIO.fail(new Error("SERVER ERROR")))).getResponse
        assertM(res)(isResponse(responseStatus(500)))
      },
      testM("status is 404") {
        val res = HttpEndpoint.mount(Http.empty.contramap[Request](i => i)).getResponse
        assertM(res)(isResponse(responseStatus(404)))
      },
      testM("status is 200 in collectM") {
        val res = HttpEndpoint.mount(Http.collectM[Request](_ => UIO(Ok))).getResponse
        assertM(res)(isResponse(responseStatus(200)))
      },
    )
  }

  /**
   * Spec for asserting behavior of an failing endpoint
   */
  def FailSpec = {
    suite("fail(cause)")(
      testM("status is 500") {
        val res = HttpEndpoint.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(responseStatus(500)))
      },
      testM("content is SERVER_ERROR") {
        val res = HttpEndpoint.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(isContent(hasBody("SERVER_ERROR"))))
      },
      testM("headers are set") {
        val res = HttpEndpoint.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(responseHeader("content-length")))
      },
    )
  }

  /**
   * Spec for an Endpoint that succeeds with a succeeding Http
   */
  def OkSpec = {
    suite("succeed(ok)")(
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(responseStatus(200)))
      },
      suite("POST")(
        testM("status is 200") {
          val content = List("A", "B", "C")
          val res     = HttpEndpoint.mount(Http.succeed(Ok)).getResponse(method = HttpMethod.POST, content = content)
          assertM(res)(isResponse(responseStatus(200)))
        },
      ),
      testM("headers are empty") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(noHeader))
      },
      testM("headers are set") {
        val res = HttpEndpoint.mount(Http.succeed(AnyResponse(headers = List(Header("key", "value"))))).getResponse
        assertM(res)(isResponse(responseHeader("key", "value")))
      },
      testM("version is 1.1") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(version("HTTP/1.1")))
      },
      testM("version is 1.1") {
        val res = HttpEndpoint.mount(Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(version("HTTP/1.1")))
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
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(responseStatus(404)))
        },
        testM("headers are empty") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(noHeader))
        },
        testM("version is 1.1") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(version("HTTP/1.1")))
        },
        testM("version is 1.1") {
          val res = HttpEndpoint.empty.getResponse
          assertM(res)(isResponse(version("HTTP/1.1")))
        },
      ),
      suite("POST")(
        testM("status is 404") {
          val res = HttpEndpoint.empty.getResponse(method = HttpMethod.POST, content = List("A", "B", "C"))
          assertM(res)(isResponse(responseStatus(404)))
        },
      ),
    )
  }

  def EchoStreamingResponseSpec = {
    val streamingResponse = AnyResponse(content =
      HttpData.fromStream(
        ZStream
          .fromIterable(List("A", "B", "C", "D"))
          .map(text => Chunk.fromArray(text.getBytes))
          .flattenChunks,
      ),
    )

    suite("StreamingResponse") {
      testM("status is 200") {
        val res = HttpEndpoint.mount(Http.collect[Request](_ => streamingResponse)).getResponse
        assertM(res)(isResponse(responseStatus(200)))
      } +
        testM("content is 'ABCD'") {
          val content = HttpEndpoint.mount(Http.collect[Request](_ => streamingResponse)).getContent
          assertM(content)(equalTo("ABCD"))
        } @@ nonFlaky
    }
  }

  /**
   * Captures scenarios when an invalid message is sent to the Endpoint.
   */
  def IllegalMessageSpec = suite("IllegalMessage")(
    testM("throws exception") {
      val program = EndpointClient.deploy(HttpEndpoint.empty).flatMap(_.write("ILLEGAL_MESSAGE").either)
      assertM(program)(isLeft(equalTo(InvalidMessage("ILLEGAL_MESSAGE"))))
    },
  )

  def ContentDecoderSpec = suite("ContentDecoder")(
    testM("status is 200") {
      val res = HttpEndpoint.mount(Http.collect[Request] { _ => Ok }).getResponse
      assertM(res)(isResponse(responseStatus(200)))
    },
    testM("content is ABCD") {
      val content = for {
        content <- Ref.make("")
        client  <- EndpointClient.deploy {
          HttpEndpoint.mount(Http.collectM[Request] { req =>
            req.decodeContent(ContentDecoder.text).flatMap(text => content.set(text).as(Ok))
          })
        }
        _       <- client.request()
        _       <- client.end("A", "B", "C", "D")
        data    <- content.get
      } yield data
      assertM(content)(equalTo("ABCD"))
    },
    testM("custom") {
      val decoder = ContentDecoder.collect(Chunk[Byte]()) { case (a, b, isLast) =>
        val c = b ++ a
        ZIO((if (isLast) Option(c) else None, c))
      }

      val content = for {
        content <- Ref.make("")
        client  <- EndpointClient.deploy {
          HttpEndpoint.mount(Http.collectM[Request] { req =>
            req.decodeContent(decoder).flatMap(text => content.set(new String(text.toArray)).as(Ok))
          })
        }
        _       <- client.request()
        _       <- client.end("A", "B", "C", "D")
        data    <- content.get
      } yield data
      assertM(content)(equalTo("ABCD"))
    },
    testM("remoteAddress") {
      val content = for {
        content <- Ref.make(Option.empty[InetAddress])
        client  <- EndpointClient.deploy {
          HttpEndpoint.mount(Http.collectM[Request] { req =>
            content.set(req.remoteAddress).as(Ok)
          })
        }
        _       <- client.request()
        data    <- content.get
      } yield data
      assertM(content)(isNone)
    },
  )
}
