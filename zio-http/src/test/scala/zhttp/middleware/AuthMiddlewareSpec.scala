package zhttp.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zhttp.http._
import zhttp.http.middleware.AuthMiddleware.{basicAuth, jwt}
import zio.clock.Clock
import zio.test.Assertion.{equalTo, isSome}
import zio.test.{DefaultRunnableSpec, assertM}
import zio.{UIO, ZIO}

object AuthMiddlewareSpec extends DefaultRunnableSpec {

  val app: HttpApp[Any with Clock, Nothing] = HttpApp.collectM { case Method.GET -> !! / "health" =>
    UIO(Response.ok)
  }
  val req                                   = Request(url = URL(!! / "health"))

  final case class HeadersHolder(headers: List[Header]) extends HeaderExtension[HeadersHolder] { self =>
    override def addHeaders(headers: List[Header]): HeadersHolder =
      HeadersHolder(self.headers ++ headers)

    override def removeHeaders(headers: List[String]): HeadersHolder =
      HeadersHolder(self.headers.filterNot(h => headers.contains(h.name)))
  }

  def spec = suite("AuthMiddleware") {
    suite("basicAuth") {
      testM("Request should succeed if basic authentication succeed") {
        val program = (app @@ basicAuth((u, p) => ZIO.succeed(u.reverse == p)))
          .apply(req.addHeaders(List(Header.basicHttpAuthorization("user", "resu"))))
        assertM(program.map(_.status))(equalTo(Status.OK))
      } +
        testM("Request should fail with status Unauthorized if basic authentication is not met") {
          val program = (app @@ basicAuth((u, p) => ZIO.succeed(u.reverse == p)))
            .apply(req.addHeaders(List(Header.basicHttpAuthorization("user", "PasswordIsNotReverse"))))
          assertM(program.map(_.status))(equalTo(Status.UNAUTHORIZED))
        } +
        testM("Responses sould have WWW-Authentication header if Basic Auth failed") {
          val program = (app @@ basicAuth((u, p) => ZIO.succeed(u.reverse == p))).apply(req)
          assertM(program.map(r => HeadersHolder(r.headers).getHeader(HttpHeaderNames.WWW_AUTHENTICATE)))(isSome)
        }
    } +
      suite("jwt") {
        testM("Request should succeed if access token can be decoded") {
          val program = (app @@ jwt("secretKey")).apply(
            req.addHeaders(
              List(
                Header.custom(
                  "X-ACCESS-TOKEN",
                  Jwt.encode(JwtClaim { s"""{"user": "someusername"}""" }, "secretKey", JwtAlgorithm.HS512),
                ),
              ),
            ),
          )
          assertM(program.map(_.status))(equalTo(Status.OK))
        } +
          testM("Request fail with status Unauthorized if access token could not be decoded") {
            val program = (app @@ jwt("incorrectSecretKey")).apply(
              req.addHeaders(
                List(
                  Header.custom(
                    "X-ACCESS-TOKEN",
                    Jwt.encode(JwtClaim { s"""{"user": "someusername"}""" }, "secretKey", JwtAlgorithm.HS512),
                  ),
                ),
              ),
            )
            assertM(program.map(_.status))(equalTo(Status.UNAUTHORIZED))
          }
      } +
      suite("combine") {
        testM("should succeed only if 2 authentications pass") {
          val program = (app @@ (basicAuth((u, p) => ZIO.succeed(u.reverse == p)) ++ jwt("secretKey"))).apply(
            req.addHeaders(
              List(
                Header.custom(
                  "X-ACCESS-TOKEN",
                  Jwt.encode(JwtClaim { s"""{"user": "someusername"}""" }, "secretKey", JwtAlgorithm.HS512),
                ),
                Header.basicHttpAuthorization("user", "resu"),
              ),
            ),
          )
          assertM(program.map(_.status))(equalTo(Status.OK))
        } +
          testM("should fail with status Unauthorized if even 1 of the authentications fail") {
            val program = (app @@ (basicAuth((u, p) => ZIO.succeed(u.reverse == p)) ++ jwt("secretKey")))
              .apply(req.addHeaders(List(Header.basicHttpAuthorization("user", "resu"))))
            assertM(program.map(_.status))(equalTo(Status.UNAUTHORIZED))
          }
      }
  }

}
