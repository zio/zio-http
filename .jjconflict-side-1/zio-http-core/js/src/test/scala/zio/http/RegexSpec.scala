package zio.http

import zio._
import zio.test._

object RegexSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RegexSpec")(
      suite("Header")(
        test("AcceptLanguage") {
          ZIO.succeed(Header.AcceptLanguage).isSuccess.map(s => assertTrue(s))
        },
        test("ContentDisposition") {
          ZIO.succeed(Header.ContentDisposition).isSuccess.map(s => assertTrue(s))
        },
        test("ContentMd5") {
          ZIO.succeed(Header.ContentMd5).isSuccess.map(s => assertTrue(s))
        },
        test("ContentRange") {
          ZIO.succeed(Header.ContentRange).isSuccess.map(s => assertTrue(s))
        },
        test("ContentSecurityPolicy.Source") {
          ZIO.succeed(Header.ContentSecurityPolicy.Source).isSuccess.map(s => assertTrue(s))
        },
        test("ContentSecurityPolicy.TrustedTypesValue") {
          ZIO.succeed(Header.ContentSecurityPolicy.TrustedTypesValue).isSuccess.map(s => assertTrue(s))
        },
        test("ContentSecurityPolicy") {
          ZIO.succeed(Header.ContentSecurityPolicy).isSuccess.map(s => assertTrue(s))
        },
        test("ContentTransferEncoding") {
          ZIO.succeed(Header.ContentTransferEncoding).isSuccess.map(s => assertTrue(s))
        },
        test("From") {
          ZIO.succeed(Header.From).isSuccess.map(s => assertTrue(s))
        },
        test("Trailer") {
          ZIO.succeed(Header.Trailer).isSuccess.map(s => assertTrue(s))
        },
        test("UserAgent") {
          ZIO.succeed(Header.UserAgent).isSuccess.map(s => assertTrue(s))
        },
        test("WWWAuthenticate") {
          ZIO.succeed(Header.WWWAuthenticate).isSuccess.map(s => assertTrue(s))
        },
        test("WWWAuthenticate") {
          ZIO.succeed(Header.WWWAuthenticate).isSuccess.map(s => assertTrue(s))
        },
      ),
      suite("HttpContentCodec")(
        test("HttpContentCodec") {
          ZIO.succeed(codec.HttpContentCodec).isSuccess.map(s => assertTrue(s))
        },
      ),
      suite("OpenAPI")(
        test("Key") {
          ZIO.succeed(endpoint.openapi.OpenAPI.Key).isSuccess.map(s => assertTrue(s))
        },
        test("Path") {
          ZIO.succeed(endpoint.openapi.OpenAPI.Path).isSuccess.map(s => assertTrue(s))
        },
        test("LiteralOrExpression") {
          ZIO.succeed(endpoint.openapi.OpenAPI.LiteralOrExpression).isSuccess.map(s => assertTrue(s))
        },
      ),
    )
}
