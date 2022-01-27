package zhttp.http

import zio.test.Assertion.{equalTo, isSome}
import zio.test.{DefaultRunnableSpec, _}

object RequestSpec extends DefaultRunnableSpec {
  def spec = suite("Request") {
    suite("host header") {
      suite("without port") {
        test("non-ssl add hostname") {
          val url = URL.fromString("http://example.com").getOrElse(null)
          val req = Request(url = url)
          assert(req.getHost)(isSome(equalTo("example.com")))
        } +
          test("ssl add hostname") {
            val url = URL.fromString("https://example.com").getOrElse(null)
            val req = Request(url = url)
            assert(req.getHost)(isSome(equalTo("example.com")))
          }
      } +
        suite("add port") {
          test("non-ssl with non-default port") {
            val url = URL.fromString("http://example.com:8090").getOrElse(null)
            val req = Request(url = url)
            assert(req.getHost)(isSome(equalTo("example.com:8090")))
          } +
            test("non-ssl with default port") {
              val url = URL.fromString("http://example.com:80").getOrElse(null)
              val req = Request(url = url)
              assert(req.getHost)(isSome(equalTo("example.com")))
            } +
            test("ssl with non-default port") {
              val url = URL.fromString("https://example.com:8090").getOrElse(null)
              val req = Request(url = url)
              assert(req.getHost)(isSome(equalTo("example.com:8090")))
            } +
            test("ssl with default port") {
              val url = URL.fromString("https://example.com:443").getOrElse(null)
              val req = Request(url = url)
              assert(req.getHost)(isSome(equalTo("example.com")))
            }
        }
    }
  }
}
