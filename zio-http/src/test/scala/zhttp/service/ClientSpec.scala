package zhttp.service

import zhttp.http.{Header, HttpData, Method, URL}
import zhttp.internal.HttpRunnableSpec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.Task
import zio.test.Assertion.{anything, containsString, isEmpty, isNonEmpty}
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("http://api.github.com/users/zio/repos", ClientSSLOptions.DefaultSSL)
      assertM(actual)(anything)
    } +
      testM("non empty content") {
        val actual          = Client.request("https://api.github.com/users/zio", ClientSSLOptions.DefaultSSL)
        val responseContent = actual.flatMap(_.getBody)
        assertM(responseContent)(isNonEmpty)
      }
      +
      testM("POST request expect non empty response content") {
        val url             = Task.fromEither(URL.fromString("https://api.github.com/users/zio"))
        val endpoint        = url.map(u => Method.POST -> u)
        val headers         = List(Header.userAgent("zio-http test"))
        val response        = endpoint.flatMap { e =>
          Client.request(e, headers, HttpData.fromText("test"))
        }
        val responseContent = response.flatMap(_.getBody)
        assertM(responseContent)(isNonEmpty)
      }
      +
      testM("empty content") {
        val actual          = Client.request("http://api.github.com/users/zio/repos", ClientSSLOptions.DefaultSSL)
        val responseContent = actual.flatMap(_.getBody)
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val actual          = Client.request("https://www.google.com", ClientSSLOptions.DefaultSSL)
        val responseContent = actual.flatMap(_.getBodyAsString)
        assertM(responseContent)(containsString("Google"))
      },
  ).provideCustomLayer(env)
}
