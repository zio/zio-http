package example.auth.digest.test

import example.auth.digest.test.DigestAuthService._
import zio._
import zio.http.Method

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

trait DigestAuthService {

  def generateNonce: UIO[String]

  def computeDigest(params: DigestParams): Task[Digest]

  def validateDigest(response: Digest, params: DigestParams): Task[Boolean]

}

object DigestAuthService {
  sealed trait HashAlgorithm
  object HashAlgorithm {
    case object MD5    extends HashAlgorithm
    case object SHA256 extends HashAlgorithm
  }

  final case class DigestParams(
    username: String,
    realm: String,
    uri: URI,
    algorithm: HashAlgorithm,
    qop: String = "auth",
    cnonce: String,
    nonce: String,
    nc: Int,
    userhash: Boolean = false,
    password: String,
    method: Method,
  )

  final case class Digest(value: String) extends AnyVal
}

case class DigestAuthLive() extends DigestAuthService {
  override def generateNonce: UIO[String] =
    Random.nextBytes(16).map(_.map("%02x".format(_)).mkString)

  private def computeHash(input: String, algorithm: HashAlgorithm): UIO[String] =
    ZIO.succeed {
      val digest = algorithm match {
        case HashAlgorithm.MD5    => MessageDigest.getInstance("MD5")
        case HashAlgorithm.SHA256 => MessageDigest.getInstance("SHA-256")
      }

      digest
        .digest(input.getBytes(StandardCharsets.UTF_8))
        .map("%02x".format(_))
        .mkString
    }

  private def computeHA1(params: DigestParams): Task[String] = {
    val a1 =
      if (params.userhash)
        s"${params.username}:${params.password}"
      else
        s"${params.username}:${params.realm}:${params.password}"

    computeHash(a1, params.algorithm)
  }

  private def computeHA2(params: DigestParams): Task[String] = {
    val a2 = s"${params.method}:${params.uri.toString}"
    computeHash(a2, params.algorithm)
  }

  private def computeResponse(ha1: String, ha2: String, params: DigestParams): Task[String] = {
    params.qop.toLowerCase match {
      case "auth" | "auth-int" =>
        val ncHex          = "%08x".format(params.nc)
        val responseString = s"$ha1:${params.nonce}:$ncHex:${params.cnonce}:${params.qop}:$ha2"
        computeHash(responseString, params.algorithm)
      case _                   =>
        val responseString = s"$ha1:${params.nonce}:$ha2"
        computeHash(responseString, params.algorithm)
    }
  }

  override def computeDigest(params: DigestParams): Task[Digest] =
    for {
      ha1      <- computeHA1(params)
      ha2      <- computeHA2(params)
      response <- computeResponse(ha1, ha2, params)
    } yield Digest(response)

  override def validateDigest(response: Digest, params: DigestParams): Task[Boolean] =
    computeDigest(params).map(_ == response)

}

object DigestAuthLive {
  def layer: ULayer[DigestAuthService] =
    ZLayer.succeed(DigestAuthLive())
}

// Example usage with ZIO App
object DigestAuthExample extends ZIOAppDefault {

  def run: ZIO[Any, Any, Unit] = {
    val program = for {
      nonce  <- ZIO.serviceWithZIO[DigestAuthService](_.generateNonce)
      cnonce <- ZIO.serviceWithZIO[DigestAuthService](_.generateNonce)

      params = DigestParams(
        username = "testuser",
        realm = "testrealm",
        uri = URI.create("/test"),
        cnonce = cnonce,
        nonce = nonce,
        nc = 1,
        password = "testpass",
        algorithm = HashAlgorithm.MD5,
        method = Method.GET,
      )

      response <- ZIO.serviceWithZIO[DigestAuthService](_.computeDigest(params))
      _        <- Console.printLine(s"Generated digest response: ${response.value}")

      isValid <- ZIO.serviceWithZIO[DigestAuthService](_.validateDigest(response, params))
      _       <- Console.printLine(s"Validation result: $isValid")

    } yield ()

    program.provide(DigestAuthLive.layer).catchAll(error => Console.printLineError(s"Error: $error"))
  }
}
