package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpVersion
import zhttp.http.URL.Location
import zhttp.service.EncodeRequest
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert}

object EncodeRequestSpec extends DefaultRunnableSpec with EncodeRequest {

  val request: Request[Any, Nothing, Complete] = Request(
    Method.GET -> URL(Path("/"), Location.Absolute(Scheme.HTTP, "localhost", 8000)),
    Nil,
    Content.fromByteBuf(Unpooled.EMPTY_BUFFER),
  )

  def spec = suite("EncodeRequest")(
    suite("encodeRequest")(
      test("should encode properly the request") {
        val encoded = encodeRequest(jVersion = HttpVersion.HTTP_1_1, req = request)
        assert(encoded.uri())(equalTo("/"))
      },
    ),
  )
}
