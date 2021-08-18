package zhttp.experiment

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpMethod
import zhttp.experiment.internal.HttpMessageAssertion
import zhttp.http.{Header, HTTP_CHARSET, Method}
import zhttp.service.EventLoopGroup
import zio.test.Assertion.equalTo
import zio.test.{assertM, DefaultRunnableSpec}
import zio.test.TestAspect.nonFlaky

object HttpEndpointRequestSpec extends DefaultRunnableSpec with HttpMessageAssertion {

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
          val req     = getRequest[BufferedRequest[ByteBuf]](url = "/abc", content = List("A", "B", "C", "D", "E"))
          val content = req.flatMap(_.content.runCollect).map(_.toList.map(_.toString(HTTP_CHARSET)))
          assertM(content)(equalTo(List("A", "B", "C", "D", "E")))
        } @@ nonFlaky,
        testM("req.url is '/abc'") {
          val req = getRequest[BufferedRequest[ByteBuf]](url = "/abc")
          assertM(req)(isRequest(url("/abc")))
        },
        testM("req.method is 'GET'") {
          val req = getRequest[BufferedRequest[ByteBuf]](url = "/abc")
          assertM(req)(isRequest(method(Method.GET)))
        },
        testM("req.method is 'POST'") {
          val req = getRequest[BufferedRequest[ByteBuf]](url = "/abc", method = HttpMethod.POST)
          assertM(req)(isRequest(method(Method.POST)))
        },
        testM("req.header is 'H1: K1'") {
          val req = getRequest[BufferedRequest[ByteBuf]](url = "/abc", header = header.set("H1", "K1"))
          assertM(req)(isRequest(header(Header("H1", "K1"))))
        },
      ),
      suite("CompleteRequest")(
        testM("req.content is 'ABCDE'") {
          val req     = getRequest[CompleteRequest[ByteBuf]](url = "/abc", content = List("A", "B", "C", "D", "E"))
          val content = req.map(_.content.toString(HTTP_CHARSET))
          assertM(content)(equalTo("ABCDE"))
        },
        testM("req.url is '/abc'") {
          val req = getRequest[CompleteRequest[ByteBuf]](url = "/abc")
          assertM(req)(isRequest(url("/abc")))
        },
        testM("req.method is 'GET'") {
          val req = getRequest[CompleteRequest[ByteBuf]](url = "/abc")
          assertM(req)(isRequest(method(Method.GET)))
        },
        testM("req.method is 'POST'") {
          val req = getRequest[CompleteRequest[ByteBuf]](url = "/abc", method = HttpMethod.POST)
          assertM(req)(isRequest(method(Method.POST)))
        },
        testM("req.header is 'H1: K1'") {
          val req = getRequest[CompleteRequest[ByteBuf]](url = "/abc", header = header.set("H1", "K1"))
          assertM(req)(isRequest(header(Header("H1", "K1"))))
        },
      ),
    ).provideCustomLayer(env)
  }
}
