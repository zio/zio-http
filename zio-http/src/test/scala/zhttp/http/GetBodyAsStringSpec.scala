package zhttp.http

import zhttp.service.Client
import zio.Chunk
import zio.test.Assertion._
import zio.test._

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

object GetBodyAsStringSpec extends DefaultRunnableSpec {

  def spec = suite("getBodyAsString") {
    val charsetGen: Gen[Any, Charset] =
      Gen.fromIterable(List(UTF_8, UTF_16, UTF_16BE, UTF_16LE, US_ASCII, ISO_8859_1))

    suite("binary chunk") {
      testM("should map bytes according to charset given") {

        checkM(charsetGen) { charset =>
          val request = Client
            .ClientRequest(
              URL(!!),
              headers = Headers.contentType(s"text/html; charset=$charset"),
              data = HttpData.fromChunk(Chunk.fromArray("abc".getBytes(charset))),
            )

          val encoded  = request.bodyAsString
          val expected = new String(Chunk.fromArray("abc".getBytes(charset)).toArray, charset)
          assertM(encoded)(equalTo(expected))
        }
      } +
        testM("should map bytes to default utf-8 if no charset given") {
          val request  =
            Client.ClientRequest(URL(!!), data = HttpData.fromChunk(Chunk.fromArray("abc".getBytes())))
          val encoded  = request.bodyAsString
          val expected = new String(Chunk.fromArray("abc".getBytes()).toArray, HTTP_CHARSET)
          assertM(encoded)(equalTo(expected))
        }
    }
  }
}
