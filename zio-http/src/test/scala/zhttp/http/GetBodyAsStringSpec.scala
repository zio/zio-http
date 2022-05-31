package zhttp.http

import zio.Chunk
import zio.test.Assertion._
import zio.test._

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

object GetBodyAsStringSpec extends ZIOSpecDefault {

  def spec = suite("getBodyAsString") {
    val charsetGen: Gen[Any, Charset] =
      Gen.fromIterable(List(UTF_8, UTF_16, UTF_16BE, UTF_16LE, US_ASCII, ISO_8859_1))

    suite("binary chunk") {
      test("should map bytes according to charset given") {

        check(charsetGen) { charset =>
          val request = Request(
            url = URL(!!),
            headers = Headers.contentType(s"text/html; charset=$charset"),
            data = HttpData.BinaryChunk(Chunk.fromArray("abc".getBytes(charset))),
          )

          val encoded  = request.bodyAsString
          val expected = new String(Chunk.fromArray("abc".getBytes(charset)).toArray, charset)
          assertZIO(encoded)(equalTo(expected))
        }
      } +
        test("should map bytes to default utf-8 if no charset given") {
          val request  = Request(url = URL(!!), data = HttpData.BinaryChunk(Chunk.fromArray("abc".getBytes())))
          val encoded  = request.bodyAsString
          val expected = new String(Chunk.fromArray("abc".getBytes()).toArray, HTTP_CHARSET)
          assertZIO(encoded)(equalTo(expected))
        }
    }
  }
}
