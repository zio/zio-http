package example.auth.bearer.jwt.symmetric.core

import pdi.jwt.JwtClaim
import zio._
import zio.http._
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}

object AuthMiddleware {

  case class Claim(
    username: String,
    email: String,
    role: UserRole,
  )

  object Claim {
    implicit val schema: Schema[Claim] = DeriveSchema.gen
    implicit val codec: JsonCodec[Claim] = DeriveJsonCodec.gen
  }

  def jwtAuthClaim(realm: String): HandlerAspect[JwtTokenServiceClaim, Claim] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenServiceClaim](_.verify(token.value.asString))
              .map(claim => (request, claim))
              .orElseFail(
                Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))),
              )
          case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))))
        }
      }
    }

  def jwtAuth(realm: String): HandlerAspect[JwtTokenService & UserService, User] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenService](_.verify(token.value.asString))
              .flatMap { username =>
                ZIO.serviceWithZIO[UserService](_.getUser(username))
              }
              .map(u => (request, u))
              .orElseFail(
                Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))),
              )
          case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))))
        }
      }
    }
}
