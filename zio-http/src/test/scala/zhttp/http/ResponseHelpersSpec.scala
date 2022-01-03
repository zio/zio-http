package zhttp.http

import zio.test._

object ResponseHelpersSpec extends DefaultRunnableSpec {
  val redirectSpec = {
    val location = "www.google.com"
    suite("redirectSpec")(
      test("Temporary redirect should produce a response with a TEMPORARY_REDIRECT") {
        val x = Response.redirect(location)
        assertTrue(x.status == Status.TEMPORARY_REDIRECT) &&
        assertTrue(x.getHeaderValue(HeaderNames.location).contains(location))
      } +
        test("Temporary redirect should produce a response with a location") {
          val x = Response.redirect(location)
          assertTrue(x.getHeaderValue(HeaderNames.location).contains(location))
        } +
        test("Permanent redirect should produce a response with a PERMANENT_REDIRECT") {
          val x = Response.redirect(location, true)
          assertTrue(x.status == Status.PERMANENT_REDIRECT)
        } +
        test("Permanent redirect should produce a response with a location") {
          val x = Response.redirect(location, true)
          assertTrue(x.getHeaderValue(HeaderNames.location).contains(location))
        } +
        test("Json should set content type to ApplicationJson") {
          val x = Response.json("""{"message": "Hello"}""")
          assertTrue(x.getHeaderValue(HeaderNames.contentType).contains(HeaderValues.applicationJson.toString))
        },
    )
  }

  def spec =
    suite("ResponseHelpers")(redirectSpec)
}
