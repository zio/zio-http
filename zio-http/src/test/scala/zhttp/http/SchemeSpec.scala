package zhttp.http

import io.netty.handler.codec.http.HttpScheme
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, _}

object SchemeSpec extends DefaultRunnableSpec {
  def spec = suite("Scheme")(
    suite("asString")(
      test("HTTP")(assert(Scheme.asString(Scheme.HTTP))(equalTo("http"))),
      test("HTTPS")(assert(Scheme.asString(Scheme.HTTPS))(equalTo("https"))),
    ),
    suite("fromJScheme")(
      test("HTTP")(assert(Scheme.fromJScheme(HttpScheme.HTTP))(equalTo(Option(Scheme.HTTP)))),
      test("HTTPS")(assert(Scheme.fromJScheme(HttpScheme.HTTPS))(equalTo(Option(Scheme.HTTPS)))),
    ),
    suite("fromString")(
      test("http")(assert(Scheme.fromString("httP"))(equalTo(Option(Scheme.HTTP)))),
      test("https")(assert(Scheme.fromString("httPs"))(equalTo(Option(Scheme.HTTPS)))),
    ),
  )
}
