package zhttp.experiment

import io.netty.handler.codec.http.HttpMethod
import zhttp.experiment.internal.HttpMessageAssertions
import zhttp.http.{Header, Method}
import zhttp.service.EventLoopGroup
import zio.test.{DefaultRunnableSpec, assertM}

object HttpEndpointRequestSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  private val env = EventLoopGroup.auto(1)

  /**
   * Spec for all the possible Http Request types
   */
  override def spec = {
    suite("Request") {

      testM("req.url is '/abc'") {
        val req = getRequest(url = "/abc")
        assertM(req)(isRequest(url("/abc")))
      } +
        testM("req.method is 'GET'") {
          val req = getRequest(url = "/abc")
          assertM(req)(isRequest(method(Method.GET)))
        } +
        testM("req.method is 'POST'") {
          val req = getRequest(url = "/abc", method = HttpMethod.POST)
          assertM(req)(isRequest(method(Method.POST)))
        } +
        testM("req.header is 'H1: K1'") {
          val req = getRequest(url = "/abc", header = header.set("H1", "K1"))
          assertM(req)(isRequest(header(Header("H1", "K1"))))
        }
    }.provideCustomLayer(env)
  }
}
