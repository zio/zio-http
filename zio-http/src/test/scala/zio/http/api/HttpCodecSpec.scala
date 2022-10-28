package zio.http.api

import zio._
import zio.http._
import zio.http.api._
import zio.http.model._
import zio.test._

object HttpCodecSpec extends ZIOSpecDefault {
  val googleUrl = URL.fromString("http://google.com").toOption.get
  val usersUrl  = URL.fromString("http://mywebservice.com/users").toOption.get

  val headerExample =
    Headers.contentType("application/json") ++ Headers("X-Trace-ID", "1234")

  val emptyJson = Body.fromString("{}")

  def spec = suite("HttpCodecSpec")(
    suite("RouteCodec") {
      test("decode route with one path segment") {
        val codec = RouteCodec.literal("users")

        for {
          result <- codec.decodeRequest(Request.get(url = usersUrl))
        } yield assertTrue(result == ())
      } +
        test("encode route with one path segment") {
          val codec = RouteCodec.literal("users")

          val request = codec.encodeRequest(())

          assertTrue(request.path.toString() == "users")
        }
    } +
      suite("HeaderCodec") {
        test("dummy test") {
          assertTrue(true)
        }
      } +
      suite("BodyCodec") {
        test("dummy test") {
          assertTrue(true)
        }
      } +
      suite("QueryCodec") {
        test("dummy test") {
          assertTrue(true)
        }
      },
  )
}
