package zio.http.model.headers.values

import zio.http.model.headers.values.Accept.{AcceptValue, InvalidAcceptValue, MediaTypeWithQFactor}
import zio.http.model.{MediaType, MimeDB}
import zio.test._

object AcceptSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("Accept header suite")(
    test("parsing of invalid Accept values") {
      assertTrue(Accept.toAccept("") == InvalidAcceptValue) &&
      assertTrue(Accept.toAccept("something") == InvalidAcceptValue) &&
      assertTrue(Accept.toAccept("text/html;q=0.8, bla=q") == InvalidAcceptValue)
    },
    test("parsing of valid Accept values") {
      assertTrue(Accept.toAccept("text/html") == AcceptValue(List(MediaTypeWithQFactor(text.`html`, None)))) &&
      assertTrue(
        Accept.toAccept("text/html;q=0.8") == AcceptValue(List(MediaTypeWithQFactor(text.`html`, Some(0.8)))),
      ) &&
      assertTrue(Accept.toAccept("text/*") == AcceptValue(List(MediaTypeWithQFactor(MediaType("text", "*"), None)))) &&
      assertTrue(Accept.toAccept("*/*") == AcceptValue(List(MediaTypeWithQFactor(MediaType("*", "*"), None)))) &&
      assertTrue(
        Accept.toAccept("*/*;q=0.1") == AcceptValue(List(MediaTypeWithQFactor(MediaType("*", "*"), Some(0.1)))),
      ) &&
      assertTrue(
        Accept.toAccept("text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8") ==
          AcceptValue(
            List(
              MediaTypeWithQFactor(text.`html`, None),
              MediaTypeWithQFactor(application.`xhtml+xml`, None),
              MediaTypeWithQFactor(application.`xml`, Some(0.9)),
              MediaTypeWithQFactor(MediaType("*", "*"), Some(0.8)),
            ),
          ),
      )
    },
    test("parsing and encoding is symmetrical") {
      val results = allMediaTypes.map(mediaType => Accept.fromAccept(Accept.toAccept(mediaType.fullType)))
      assertTrue(allMediaTypes.map(_.fullType) == results)
    },
  )
}
