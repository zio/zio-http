package zhttp.http

import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}
import zio.ZIO
import zio.test.Assertion._
import zio.test._

import scala.util.Try

object ResponseSpec extends DefaultRunnableSpec {
  def spec = suite("Response")(
    suite("redirect") {
      val location = "www.google.com"
      test("Temporary redirect should produce a response with a TEMPORARY_REDIRECT") {
        val x = Response.redirect(location)
        assertTrue(x.status == Status.TEMPORARY_REDIRECT) &&
        assertTrue(x.headerValue(HeaderNames.location).contains(location))
      } +
        test("Temporary redirect should produce a response with a location") {
          val x = Response.redirect(location)
          assertTrue(x.headerValue(HeaderNames.location).contains(location))
        } +
        test("Permanent redirect should produce a response with a PERMANENT_REDIRECT") {
          val x = Response.redirect(location, true)
          assertTrue(x.status == Status.PERMANENT_REDIRECT)
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
        testM("should convert response to Http") {
          val http = Http(Response.ok)
          assertM(http(()))(equalTo(Response.ok))
        },
      ) +
      suite("unsafeEncode")(
        testM("should throw error for HttpData.Incoming") {
          val unsafeRun: (UnsafeChannel => UnsafeContent => Unit) => Unit = _ => ()
          val actual = ZIO.fromTry(Try(Response(data = HttpData.Incoming(unsafeRun)).unsafeEncode()))
          assertM(actual.run)(fails(isSubtype[IllegalStateException](anything)))
        },
      ),
  )
}
