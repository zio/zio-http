package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import zhttp.experiment.{HttpMessage, Part}
import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
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
          """
            |
            |-----------------------------9051914041544843365972754266
            |Content-Disposition: form-data; name="text"
            |
            |text default
            |-----------------------------9051914041544843365972754266
            |Content-Disposition: form-data; name="file1"; filename="a.txt"
            |Content-Type: text/plain
            |
            |1: Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.Content of a.txt.
            |2: Content of a.txt.
            |3: Content of a.txt.
            |4: Content of a.txt.
            |5: Content of a.txt.
            |6: Content of a.txt.
            |7: Content of a.txt.
            |8: Content of a.txt.
            |9: Content of a.txt.
            |10: Content of a.txt.
            |11: Content of a.txt.
            |12: Content of a.txt.
            |13: Content of a.txt.
            |14: Content of a.txt.
            |15: Content of a.txt.
            |
            |-----------------------------9051914041544843365972754266
            |Content-Disposition: form-data; name="file2"; filename="a.html"
            |Content-Type: text/html
            |
            |<!DOCTYPE html><title>Content of a.html.</title>
            |
            |-----------------------------9051914041544843365972754266--
            |""".split("\\n").map(_.stripMargin + "\r\n").toList
        val contentList = content.map(x => new DefaultHttpContent(Unpooled.wrappedBuffer(x.getBytes(HTTP_CHARSET))))
        println(content.mkString(""))
        val req         = Request(
          Method.POST,
          headers = List(
            Header(
              "content-type",
              "multipart/form-data; boundary=---------------------------9051914041544843365972754266",
            ),
            Header.transferEncodingChunked,
          ),
        )
        for {
          decoder <- multipartDecoder(req)
          y       <- ZIO.foreach(contentList)(decoder.offer(_) *> decoder.poll)
        } yield assert(
          y.flatten
            .filter(_ != Part.Empty)
            .map {
              case Part.FileData(content, _) => new String(content.toArray, HTTP_CHARSET).mkString("")
              case Part.Attribute(_, value)  => value.get
              case Part.Empty                => ???
            },
        )(equalTo(List()))

      }
  }
}
