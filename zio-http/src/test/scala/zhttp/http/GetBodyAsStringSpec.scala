package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
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

      checkM(charsetGen) { charset =>
        val encoded = Request(
          Method.GET,
          URL(Path("/")),
          headers = Headers(HttpHeaderNames.CONTENT_TYPE.toString, s"text/html; charset=$charset"),
          data = HttpData.BinaryChunk(Chunk.fromArray("abc".getBytes(charset))),
        ).getBodyAsString

        val expected = new String(Chunk.fromArray("abc".getBytes(charset)).toArray, charset)

        assertM(encoded)(equalTo(expected))
      }
    } +
      testM("should map bytes to default utf-8 if no charset given") {
        val data    = Chunk.fromArray("abc".getBytes())
        val content = HttpData.BinaryChunk(data)
        val request = Request(Method.GET, URL(Path("/")), data = content)
        val encoded = request.getBodyAsString
        val actual  = new String(data.toArray, HTTP_CHARSET)
        assertM(encoded)(equalTo(actual))
      },
  )
}
