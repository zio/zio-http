package zio.http.model.headers.values

import zio.Chunk
import zio.http.api.HttpCodec.{acceptCodec, mediaTypeCodec}
import zio.http.api.internal.RichTextCodec
import zio.http.api.internal.RichTextCodec.comma
import zio.http.model.headers.values.Accept.{AcceptValue, InvalidAcceptValue, MediaTypeWithQFactor}
import zio.http.model.{MediaType, MimeDB}
import zio.test.Assertion.{equalTo, isRight}
import zio.test._

object AcceptSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("Accept header suite")(
    test("parsing of invalid Accept values") {
      println(mediaTypeCodec.describe.toString)
      val probe = (mediaTypeCodec ~
        (RichTextCodec.literal(";q=").optional("")))
        .decode("text/html, application/json") // , application/json, text/plain, */*")

      println(probe)

      // assert(probe.map(Accept.toAccept))(isRight(equalTo(InvalidAcceptValue)))
      assertTrue(Accept.toAccept(Chunk.empty) == InvalidAcceptValue) &&
      assertTrue(Accept.toAccept(Chunk(("something", 1.0))) == InvalidAcceptValue) &&
      assertTrue(Accept.toAccept(Chunk(("text/html", 0.8))) == InvalidAcceptValue)
    },
//    test("parsing of valid Accept values") {
//      assertTrue(
//        Accept.toAccept("text/html") == AcceptValue(Chunk(MediaTypeWithQFactor(text.`html`, None))),
//      ) &&
//      assertTrue(
//        Accept.toAccept("text/html;q=0.8") ==
//          AcceptValue(Chunk(MediaTypeWithQFactor(text.`html`.withQFactor(0.8), Some(0.8)))),
//      ) &&
//      assertTrue(
//        Accept.toAccept("text/*") == AcceptValue(Chunk(MediaTypeWithQFactor(MediaType("text", "*"), None))),
//      ) &&
//      assertTrue(
//        Accept.toAccept("*/*") == AcceptValue(Chunk(MediaTypeWithQFactor(MediaType("*", "*"), None))),
//      ) &&
//      assertTrue(
//        Accept.toAccept("*/*;q=0.1") ==
//          AcceptValue(Chunk(MediaTypeWithQFactor(MediaType("*", "*").withQFactor(0.1), Some(0.1)))),
//      ) &&
//      assertTrue(
//        Accept.toAccept("text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8") ==
//          AcceptValue(
//            Chunk(
//              MediaTypeWithQFactor(text.`html`, None),
//              MediaTypeWithQFactor(application.`xhtml+xml`, None),
//              MediaTypeWithQFactor(application.`xml`.withQFactor(0.9), Some(0.9)),
//              MediaTypeWithQFactor(MediaType("*", "*").withQFactor(0.8), Some(0.8)),
//            ),
//          ),
//      )
//    },
//    test("parsing and encoding is symmetrical") {
//      val results = allMediaTypes.map(mediaType => Accept.fromAccept(Accept.toAccept(mediaType.fullType)))
//      assertTrue(allMediaTypes.map(_.fullType) == results)
//    },
  )

  implicit class MediaTypeTestOps(mediaType: MediaType) {
    def withQFactor(double: Double): MediaType = {
      mediaType
        .copy(parameters = Map("q" -> double.toString))
    }
  }
}
