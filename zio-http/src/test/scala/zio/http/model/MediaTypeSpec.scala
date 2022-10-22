package zio.http.model

import zio.test._

object MediaTypeSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("MediaTypeSpec")(
    test("predefined mime type parsing") {
      assertTrue(MediaType.forContentType("application/json").contains(application.`json`))
    },
    test("custom mime type parsing") {
      assertTrue(MediaType.parseCustomMediaType("custom/mime").contains(MediaType("custom", "mime")))
    },
    test("optional parameter parsing") {
      assertTrue(MediaType.forContentType("application/json;p1=1;p2=2;p3=\"quoted\"")
        .contains(
          application.`json`.copy(parameters = Map("p1" -> "1", "p2" -> "2", "p3" -> "\"quoted\""))
        )
      )
    }
  )
}
