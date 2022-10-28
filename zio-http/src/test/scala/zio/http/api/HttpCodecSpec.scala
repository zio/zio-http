package zio.http.api

import zio._
import zio.http._
import zio.http.api._
import zio.http.model._
import zio.test._

object HttpCodecSpec extends ZIOSpecDefault {
  val googleUrl  = URL.fromString("http://google.com").toOption.get
  val usersUrl   = URL.fromString("http://mywebservice.com/users").toOption.get
  val usersIdUrl = URL.fromString("http://mywebservice.com/users/42").toOption.get

  val headerExample =
    Headers.contentType("application/json") ++ Headers("X-Trace-ID", "1234")

  val emptyJson = Body.fromString("{}")

  def spec = suite("HttpCodecSpec")(
    suite("RouteCodec") {
      test("decode route with one path segment") {
        val codec = RouteCodec.literal("users")

        for {
          result <- codec.decodeRequest(Request.get(url = usersUrl))
        } yield assertTrue(result == (()))
      } +
        test("encode route with one path segment") {
          val codec = RouteCodec.literal("users")

          val request = codec.encodeRequest(())

          assertTrue(request.path.toString() == "users")
        } +
        test("decode route with two path segments") {
          val userCodec = RouteCodec.literal("users")
          val intCodec  = RouteCodec.int

          // /users/<int id>
          val fullCodec = userCodec ++ intCodec

          for {
            result <- fullCodec.decodeRequest(Request.get(url = usersIdUrl))
          } yield assertTrue(result == 42)
        } +
        test("encode route with two path segments") {
          val userCodec = RouteCodec.literal("users")
          val intCodec  = RouteCodec.int

          // /users/<int id>
          val fullCodec = userCodec ++ intCodec

          val request = fullCodec.encodeRequest(42)

          assertTrue(request.path.toString() == "users/42")
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
