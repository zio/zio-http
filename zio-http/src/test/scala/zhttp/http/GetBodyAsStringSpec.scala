package zhttp.http

import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.magnolia.DeriveGen
import zio.{Chunk, ZIO}

import java.nio.charset.StandardCharsets

object GetBodyAsStringSpec extends DefaultRunnableSpec {
  sealed trait Charset
  case object UTF_8      extends Charset
  case object UTF_16     extends Charset
  case object UTF_16BE   extends Charset
  case object UTF_16LE   extends Charset
  case object US_ASCII   extends Charset
  case object ISO_8859_1 extends Charset

  val genPoint: Gen[Random with Sized, Charset] = DeriveGen[Charset]
  def getCharset(a: Charset)                    = a match {
    case UTF_8      => StandardCharsets.UTF_8
    case UTF_16     => StandardCharsets.UTF_16
    case UTF_16BE   => StandardCharsets.UTF_16BE
    case UTF_16LE   => StandardCharsets.UTF_16LE
    case US_ASCII   => StandardCharsets.US_ASCII
    case ISO_8859_1 => StandardCharsets.ISO_8859_1
  }

  def spec = suite("getBodyAsString")(
    testM("should map bytes according to charset given") {
      val actual: ZIO[Random with Sized, Option[Nothing], Boolean] =
        for {
          a <- genPoint.runHead.get
          b = Request(
            endpoint = Method.GET -> URL(Path("/")),
            content = HttpData.CompleteData(Chunk.fromArray("abc".getBytes(getCharset(a)))),
          ).getBodyAsString(getCharset(a))
          c = Option(new String(Chunk.fromArray("abc".getBytes(getCharset(a))).toArray, getCharset(a)))
        } yield b.equals(c)

      assertM(actual)(isTrue)
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
