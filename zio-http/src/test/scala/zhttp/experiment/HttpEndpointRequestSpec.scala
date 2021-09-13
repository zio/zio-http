package zhttp.experiment

import io.netty.handler.codec.http.HttpMethod
import zhttp.experiment.internal.HttpMessageAssertions
import zhttp.http.{HTTP_CHARSET, Header, Method}
import zhttp.service.EventLoopGroup
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.nonFlaky
import zio.test.{DefaultRunnableSpec, assertM}

object HttpEndpointRequestSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  private val env = EventLoopGroup.auto(1)

  /**
   * Spec for all the possible Http Request types
   */
  override def spec = {
    suite("Request")(
      suite("AnyRequest")(
        testM("req.url is '/abc'") {
          val req = getRequest[AnyRequest](url = "/abc")
          assertM(req)(isRequest(url("/abc")))
        },
        testM("req.method is 'GET'") {
          val req = getRequest[AnyRequest](url = "/abc")
          assertM(req)(isRequest(method(Method.GET)))
        },
        testM("req.method is 'POST'") {
          val req = getRequest[AnyRequest](url = "/abc", method = HttpMethod.POST)
          assertM(req)(isRequest(method(Method.POST)))
        },
        testM("req.header is 'H1: K1'") {
          val req = getRequest[AnyRequest](url = "/abc", header = header.set("H1", "K1"))
          assertM(req)(isRequest(header(Header("H1", "K1"))))
        },
      ),
      suite("BufferedRequest")(
        testM("req.content is 'ABCDE'") {
          val req     = getRequest[Buffered](url = "/abc", content = List("A", "B", "C", "D", "E"))
          val content = req
            .flatMap(req => ZStream.fromQueue(req.content).runCollect)
            .map(_.toList.map(_.toString(HTTP_CHARSET)))
          assertM(content)(equalTo(List("A", "B", "C", "D", "E")))
        } @@ nonFlaky,
        testM("req.url is '/abc'") {
          val req = getRequest[Buffered](url = "/abc")
          assertM(req)(isRequest(url("/abc")))
        },
        testM("req.method is 'GET'") {
          val req = getRequest[Buffered](url = "/abc")
          assertM(req)(isRequest(method(Method.GET)))
        },
        testM("req.method is 'POST'") {
          val req = getRequest[Buffered](url = "/abc", method = HttpMethod.POST)
          assertM(req)(isRequest(method(Method.POST)))
        },
        testM("req.header is 'H1: K1'") {
          val req = getRequest[Buffered](url = "/abc", header = header.set("H1", "K1"))
          assertM(req)(isRequest(header(Header("H1", "K1"))))
        },
      ),
      suite("CompleteRequest")(
        testM("req.content is 'ABCDE'") {
          val req     = getRequest[Complete](url = "/abc", content = List("A", "B", "C", "D", "E"))
          val content = req.map(_.content.toString(HTTP_CHARSET))
          assertM(content)(equalTo("ABCDE"))
        },
        testM("req.url is '/abc'") {
          val req = getRequest[Complete](url = "/abc")
          assertM(req)(isRequest(url("/abc")))
        },
        testM("req.method is 'GET'") {
          val req = getRequest[Complete](url = "/abc")
          assertM(req)(isRequest(method(Method.GET)))
        },
        testM("req.method is 'POST'") {
          val req = getRequest[Complete](url = "/abc", method = HttpMethod.POST)
          assertM(req)(isRequest(method(Method.POST)))
        },
        testM("req.header is 'H1: K1'") {
          val req = getRequest[Complete](url = "/abc", header = header.set("H1", "K1"))
          assertM(req)(isRequest(header(Header("H1", "K1"))))
        },
      ),
    ).provideCustomLayer(env)
  }
}
