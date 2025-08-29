package example.auth.bearer.jwt.symmetric.core

import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import zio.Config.Secret
import zio._
import zio.json._

import java.time.Clock

trait JwtTokenServiceClaim {
  def issue(username: String, email: String, roles: Set[String]): UIO[String]
  def verify(token: String): Task[UserInfo]
}

case class JwtAuthServiceClaimLive(
  secretKey: Secret,
  tokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
) extends JwtTokenServiceClaim {
  implicit val clock: Clock = Clock.systemUTC

  override def issue(username: String, email: String, roles: Set[String]): UIO[String] =
    ZIO.succeed {
      Jwt.encode(
        claim = JwtClaim(subject = Some(username)).issuedNow
          .expiresIn(tokenTTL.toSeconds)
          .++(("roles", roles))
          .++(("email", email)),
        key = secretKey.stringValue,
        algorithm = algorithm,
      )
    }

  override def verify(token: String): ZIO[Any, Throwable, UserInfo] =
    ZIO
      .fromTry(
        Jwt.decode(token, secretKey.stringValue, Seq(algorithm)),
      )
      .filterOrFail(_.isValid)(new Exception("Token expired"))
      .map(_.toJson)
      .map(UserInfo.codec.decodeJson(_).toOption)
      .someOrFail(new Exception("Invalid token"))
}

object JwtTokenServiceClaim {
  def live(
    secretKey: Secret,
    tokenTTL: Duration = 15.minutes,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ULayer[JwtAuthServiceClaimLive] =
    ZLayer.succeed(JwtAuthServiceClaimLive(secretKey, tokenTTL, algorithm))
}

trait JwtTokenService {
  def issue(username: String): UIO[String]
  def verify(token: String): Task[String]
}

case class JwtAuthServiceLive(
  secretKey: Secret,
  tokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
) extends JwtTokenService {
  implicit val clock: Clock = Clock.systemUTC

  override def issue(username: String): UIO[String] =
    ZIO.succeed {
      Jwt.encode(
        JwtClaim(subject = Some(username)).issuedNow.expiresIn(tokenTTL.toSeconds),
        secretKey.stringValue,
        algorithm,
      )
    }

  override def verify(token: String): Task[String] =
    ZIO
      .fromTry(
        Jwt.decode(token, secretKey.stringValue, Seq(algorithm)),
      )
      .map(_.subject)
      .some
      .orElse(ZIO.fail(new Exception("Invalid token")))
}

object JwtTokenService {
  def live(
    secretKey: Secret,
    tokenTTL: Duration = 15.minutes,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ULayer[JwtAuthServiceLive] =
    ZLayer.succeed(JwtAuthServiceLive(secretKey, tokenTTL, algorithm))
}

object TestJwt extends App {
  implicit val clock: Clock = Clock.systemUTC

  case class Claim(
    @zio.json.jsonField("sub")
    username: String,
    foo: String,
    roles: Chunk[String],
  )

  object Claim {
    implicit val codec: JsonCodec[Claim] = DeriveJsonCodec.gen
  }

  println(
    JwtZIOJson
      .decodeJson(
        token = Jwt.encode(
          claim = JwtClaim(subject = Some("john")).issuedNow
            .expiresIn(3600)
            .++(("foo", "barr"))
            .++(("role", Chunk("Admin"))),
          key = "key",
          algorithm = JwtAlgorithm.HS512,
        ),
        key = "key",
        algorithms = Seq(JwtAlgorithm.HS512),
      )
      .map { json =>
        Claim.codec.decodeJson(json.toJson)
      },
  )

  case class Foo(roles: Chunk[String])
  object Foo {
    implicit val codec: JsonCodec[Foo] = DeriveJsonCodec.gen
  }
  val r = Foo(Chunk("Admin"))
  val rj  = Foo.codec.encodeJson(r, None)
  println(rj)
  val rjd = Foo.codec.decodeJson(rj)
  println(rjd)

//  println(
//    JwtZIOJson
//      .decodeJson(
//        token = Jwt.encode(
//          claim = JwtClaim(subject = Some("john")).issuedNow
//            .expiresIn(3600)
//            .++(("foo", "barr"))
//            .++(("role", UserRole.Admin)),
//          key = "key",
//          algorithm = JwtAlgorithm.HS512,
//        ),
//        key = "key",
//        algorithms = Seq(JwtAlgorithm.HS512),
//      )
//      .map { json =>
//        json.get(JsonCursor.isObject).map { obj =>
//          for {
//            username <- obj.get(JsonCursor.field("sub").isString).map(_.value)
//            role     <- obj.get(JsonCursor.field("role").isString).map(_.value).map(UserRole.fromString)
//            foo      <- obj.get(JsonCursor.field("foo").isString).map(_.value)
//          } yield Claim(username, role.getOrElse(User), foo)
//        }
//      },
//  )

//  println(Claim.codec.encodeJson(Claim("john", UserRole.Admin, "bar"), None))
}
