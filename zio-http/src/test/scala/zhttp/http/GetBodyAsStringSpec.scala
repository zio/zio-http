package zhttp.http

import zio.Chunk
import zio.test.Assertion._
import zio.test._

import java.nio.charset.StandardCharsets._

object GetBodyAsStringSpec extends DefaultRunnableSpec {

  def spec = suite("getBodyAsString")(
    testM("should map bytes according to charset given") {
      val actual: Gen[Any, Boolean] =
        for {
          a <- Gen.fromIterable(List(UTF_8, UTF_16, UTF_16BE, UTF_16LE, US_ASCII, ISO_8859_1))
          b = Request(
            endpoint = Method.GET -> URL(Path("/")),
            content = HttpData.CompleteData(Chunk.fromArray("abc".getBytes(a))),
          ).getBodyAsString(a)
          c = Option(new String(Chunk.fromArray("abc".getBytes(a)).toArray, a))
        } yield b.equals(c)

      check(actual) { actual => assert(actual)(equalTo(true)) }
    },
    test("should map bytes to default utf-8 if no charset given") {
      val data                            = Chunk.fromArray("abc".getBytes())
      val content: HttpData[Any, Nothing] = HttpData.CompleteData(data)
      val request                         = Request(endpoint = Method.GET -> URL(Path("/")), content = content)
      val encoded                         = request.getBodyAsString()
      val actual                          = Option(new String(data.toArray, HTTP_CHARSET))
      assert(actual)(equalTo(encoded))
    },
  )
}
