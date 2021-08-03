package zhttp.service

import io.netty.handler.codec.http.HttpVersion
import zhttp.http._
import zio.test.Assertion._
import zio.test._

import scala.jdk.CollectionConverters._

object EncodeResponseSpec extends DefaultRunnableSpec with EncodeResponse {

  def spec = suite("EncodeResponse")(
    test("should set multiple Headers with same name") {
      val res    = Response.http(Status.OK, List(Header.custom("Set-Cookie", "x1"), Header.custom("Set-Cookie", "x2")))
      val actual = encodeResponse(HttpVersion.HTTP_1_1, res)
      assert(actual.headers().getAll("Set-Cookie").asScala.toList)(equalTo(List("x1", "x2")))
    },
  )
}
