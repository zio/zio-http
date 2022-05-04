package zhttp.http

import zio.test.Assertion._
import zio.test._

object ResponseSpec extends ZIOSpecDefault {
  def spec = suite("Response")(
    suite("redirect") {
      val location = "www.google.com"
      test("Temporary redirect should produce a response with a TEMPORARY_REDIRECT") {
        val x = Response.redirect(location)
        assertTrue(x.status == Status.TemporaryRedirect) &&
        assertTrue(x.headerValue(HeaderNames.location).contains(location))
      } +
        test("Temporary redirect should produce a response with a location") {
          val x = Response.redirect(location)
          assertTrue(x.headerValue(HeaderNames.location).contains(location))
        } +
        test("Permanent redirect should produce a response with a PERMANENT_REDIRECT") {
          val x = Response.redirect(location, true)
          assertTrue(x.status == Status.PermanentRedirect)
        } +
        test("Permanent redirect should produce a response with a location") {
          val x = Response.redirect(location, true)
          assertTrue(x.headerValue(HeaderNames.location).contains(location))
        }
    } +
      suite("json")(
        test("Json should set content type to ApplicationJson") {
          val x = Response.json("""{"message": "Hello"}""")
          assertTrue(x.headerValue(HeaderNames.contentType).contains(HeaderValues.applicationJson.toString))
        },
      ) +
      suite("toHttp")(
        test("should convert response to Http") {
          val http = Http(Response.ok)
          assertZIO(http(()))(equalTo(Response.ok))
        },
      ),
  )
}
