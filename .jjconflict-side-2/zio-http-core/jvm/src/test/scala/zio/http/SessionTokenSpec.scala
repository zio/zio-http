package zio.http

import zio._
import zio.test._

object SessionTokenSpec extends ZIOSpecDefault {

  def spec = suite("SessionToken")(
    test("generates 100 unique tokens") {
      for {
        tokens <- ZIO.foreach((1 to 100).toList)(_ => SessionToken.generate)
      } yield assertTrue(tokens.distinct.size == 100)
    },
    test("tokens are 43 chars in [A-Za-z0-9_-]") {
      for {
        token <- SessionToken.generate
      } yield assertTrue(token.length == 43, token.matches("[A-Za-z0-9_-]+"))
    },
    test("tokens carry 32 bytes of entropy") {
      for {
        token   <- SessionToken.generate
        decoded <- ZIO.attempt(java.util.Base64.getUrlDecoder.decode(token)).orDie
      } yield assertTrue(decoded.length == SessionToken.EntropyBytes)
    },
    test("tokens are not UUID strings") {
      for {
        token  <- SessionToken.generate
        parsed <- ZIO.attempt(java.util.UUID.fromString(token)).either
      } yield assertTrue(parsed.isLeft)
    },
  )

}
