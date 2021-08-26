package zhttp.experiment
import io.netty.handler.codec.http.LastHttpContent
import zhttp.experiment.HttpMessage._
import zhttp.experiment.internal._
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio._
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._

object HttpEndpointResponseSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env = EventLoopGroup.auto(1)

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyContent(Gen.const(data))
    header  <- HttpGen.header
    status  <- HttpGen.status
    decode  <- HttpGen.canDecode
  } yield (data, content, status, header, decode)

  private val everything = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.content(Gen.const(data))
    header  <- HttpGen.header
    status  <- HttpGen.status
    decode  <- HttpGen.canDecode
  } yield (data, content, status, header, decode)

  private val contentLess = for {
    data   <- Gen.listOf(Gen.alphaNumericString)
    decode <- HttpGen.canDecode
  } yield (data, decode)

  def spec =
    suite("HttpEndpointResponse")(
      testM("response fields") {
        checkAllM(everything) { case (data, content, status, header, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.collect(_ => AnyResponse(status, List(header), content)))
          assertM(endpoint.getResponse(content = data))(isResponse {
            responseStatus(status.asJava.code()) &&
            responseHeader(header) &&
            version("HTTP/1.1")
          })
        }
      },
      testM("response non-empty content") {
        checkAllM(nonEmptyContent) { case (data, content, status, header, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.collect(_ => AnyResponse(status, List(header), content)))
          assertM(endpoint.getContent(content = data))(equalTo(data.mkString("")))
        }
      },
      testM("failing Http") {
        checkM(contentLess) { case (data, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.fail(new Error("SERVER ERROR")))
          assertM(endpoint.getResponse(content = data))(isResponse {
            responseStatus(500) && version("HTTP/1.1")
          })
        }
      },
      testM("failing effect") {
        checkM(contentLess) { case (data, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.collectM(_ => ZIO.fail(new Error("SERVER ERROR"))))
          assertM(endpoint.getResponse(content = data))(isResponse {
            responseStatus(500) && version("HTTP/1.1")
          })
        }
      },
      testM("empty Http") {
        checkM(contentLess) { case (data, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.empty)
          assertM(endpoint.getResponse(content = data))(isResponse {
            responseStatus(404) && version("HTTP/1.1") && noHeader
          })
        }
      },
      testM("404 content") {
        checkM(contentLess) { case (data, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.empty)
          assertM(endpoint.getHttpContent(content = data))(equalTo(Chunk(LastHttpContent.EMPTY_LAST_CONTENT)))
        }
      },
      testM("404 non-empty content") {
        checkAllM(contentLess) { case (data, decode) =>
          val endpoint =
            HttpEndpoint.mount(!! / "a", decode)(Http.collect(_ => AnyResponse()))
          assertM(endpoint.getHttpContent(content = data))(equalTo(Chunk(LastHttpContent.EMPTY_LAST_CONTENT)))
        }
      },
      testM("successful effect") {
        checkM(everything) { case (data, content, status, header, decode) =>
          val endpoint = HttpEndpoint.mount(decode)(Http.collectM(_ => UIO(AnyResponse(status, List(header), content))))
          assertM(endpoint.getResponse(content = data))(isResponse {
            responseStatus(status.asJava.code()) && version("HTTP/1.1")
          })
        }
      },
    ).provideCustomLayer(env) @@ timeout(10 seconds)
}
