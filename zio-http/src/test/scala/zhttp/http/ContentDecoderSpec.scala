package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zhttp.experiment.{HttpMessage, Part}
import zhttp.http.ContentDecoder.multipartDecoder
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test._

object ContentDecoderSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder") {
    testM("text") {
      checkAllM(content) { c => assertM(ContentDecoder.text.decode(c))(equalTo("ABCD")) }
    } +
      testM("collectAll") {
        checkAllM(content) { c =>
          val sampleStepDecoder = ContentDecoder.collectAll[HttpMessage[HttpContent]]
          assertM(sampleStepDecoder.decode(c).map(_.map(_.data).flatten).map(ch => new String(ch.toArray)))(
            equalTo("ABCD"),
          )
        }
      } +
      testM("Multipart") {
        val content = HttpGen.nonEmptyHttpData(Gen.const(List("a", "ab", "abc", "abcd", "abcde", "abcdef")))
        checkAllM(content) { c =>
          {
            val decoder                                 = ContentDecoder.multipart(ContentDecoder.testDecoder)
            val content: ZIO[Any, Throwable, List[Int]] =
              decoder.decode(c).flatMap(b => b.takeAll.map(_.map(_.raw)))
            assertM(content)(
              equalTo(List(21)),
            )
          }
        }
      } +
      testM("MultipartDecoder") {
        val content     =
          s"""-----------------------------9051914041544843365972754266
            |Content-Disposition: form-data; name="file1"; filename="a.txt"
            |Content-Type: text/plain
            |
            |1
            |2
            |3
            |-----------------------------9051914041544843365972754266--
            |""".split("\\n").map(_.stripMargin + "\r\n").toList
        val contentList = content.map(x => new DefaultHttpContent(Unpooled.wrappedBuffer(x.getBytes(HTTP_CHARSET))))
        val req         = Request(
          Method.POST,
          headers = List(
            Header(
              "content-type",
              "multipart/form-data; boundary=---------------------------9051914041544843365972754266",
            ),
          ),
        )
        for {
          decoder <- multipartDecoder(req)
          y       <- ZIO.foreach(contentList)(decoder.offer(_) *> decoder.poll)
          x = y.flatten
            .filter(_.isInstanceOf[Part.FileData])
            .filter(_.asInstanceOf[Part.FileData].content.nonEmpty)
            .map {
              case Part.FileData(content, _) =>
                content.toArray.map(_.toChar).mkString("").trim
              case _                         => ???
            }
            .foldLeft("")(_ + _)
        } yield assert(
          x,
        )(equalTo("123"))

      }
  }
}
