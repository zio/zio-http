package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.service.Client
import zio.Chunk
import zio.test.Assertion._
import zio.test._

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

object GetBodyAsStringSpec extends DefaultRunnableSpec {

  def spec = suite("getBodyAsString")(
    testM("should map bytes according to charset given") {
      val charsetGen: Gen[Any, Charset] =
        Gen.fromIterable(List(UTF_8, UTF_16, UTF_16BE, UTF_16LE, US_ASCII, ISO_8859_1))

      check(charsetGen) { charset =>
        val encoded = Client
          .ClientRequest(
            Method.GET,
            URL(Path("/")),
            headers = Headers(HttpHeaderNames.CONTENT_TYPE.toString, s"text/html; charset=$charset"),
            data = HttpData.BinaryChunk(Chunk.fromArray("abc".getBytes())),
          )
          .getBodyAsString
        val actual  = Option(new String(Chunk.fromArray("abc".getBytes(charset)).toArray, charset))

        assert(actual)(equalTo(encoded))
      }
    } +
      test("should map bytes to default utf-8 if no charset given") {
        val data    = Chunk.fromArray("abc".getBytes())
        val content = HttpData.BinaryChunk(data)
        val request = Client.ClientRequest(Method.GET, URL(Path("/")), data = content)
        val encoded = request.getBodyAsString
        val actual  = Option(new String(data.toArray, HTTP_CHARSET))
        assert(actual)(equalTo(encoded))
      },
  )
}
