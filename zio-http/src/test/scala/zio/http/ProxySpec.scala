package zio.http

import zio.http.middleware.Auth.Credentials
import zio.test.Assertion.{equalTo, isNone, isNull, isSome}
import zio.test._

object ProxySpec extends ZIOSpecDefault {
  private val validUrl = URL.fromString("http://localhost:8123").toOption.getOrElse(URL.empty)

  override def spec = suite("Proxy")(
    suite("Authenticated Proxy")(
      test("successfully encode valid proxy") {
        val username = "unameTest"
        val password = "upassTest"
        val proxy    = Proxy(validUrl, Some(Credentials(username, password)))
        val encoded  = proxy.encode

        assert(encoded.map(_.username()))(isSome(equalTo(username))) &&
        assert(encoded.map(_.password()))(isSome(equalTo(password))) &&
        assert(encoded.map(_.authScheme()))(isSome(equalTo("basic")))
      },
      test("fail to encode invalid proxy") {
        val proxy   = Proxy(URL.empty)
        val encoded = proxy.encode

        assert(encoded.map(_.username()))(isNone)
      },
    ),
    suite("Unauthenticated proxy")(
      test("successfully encode valid proxy") {
        val proxy   = Proxy(validUrl)
        val encoded = proxy.encode

        assert(encoded)(isSome) &&
        assert(encoded.map(_.username()))(isSome(isNull)) &&
        assert(encoded.map(_.password()))(isSome(isNull)) &&
        assert(encoded.map(_.authScheme()))(isSome(equalTo("none")))
      },
    ),
  )
}
