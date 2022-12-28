package zio.http.api

import zio.http._
import zio.http.model.MediaType
import zio.http.model.headers.values.AccessControlMaxAge
import zio.test._

object ProxyAuthoriazationMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("ProxyAuthoriazationMiddlewareSpec")(
      suite("valid values")(
        test("add valid Basic ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("Basic YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("Basic YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid Bearer ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("Bearer YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("Bearer YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid Digest ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("Digest YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("Digest YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid HOBA ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("HOBA YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("HOBA YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid Mutual ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("Mutual YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("Mutual YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid Negotiate ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("Negotiate YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("Negotiate YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid OAuth ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("OAuth YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("OAuth YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid OAuth ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("OAuth YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("OAuth YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid SCRAM-SHA-1 ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("SCRAM-SHA-1 YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("SCRAM-SHA-1 YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid SCRAM-SHA-256 ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("SCRAM-SHA-256 YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("SCRAM-SHA-256 YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
        test("add valid vapid ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("vapid YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.proxyAuthorization.getOrElse("error").equals("vapid YWxhZGRpbjpvcGVuc2VzYW1l"),
          )
        },
      ),
      suite("invalid values")(
        test("add invalid ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("!!@#$%(")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.proxyAuthorization.getOrElse("error").equals(""))
        },
        test("add invalid empty authorization ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization(" YWxhZGRpbjpvcGVuc2VzYW1l")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.proxyAuthorization.getOrElse("error").equals(""))
        },
        test("add invalid empty credential ProxyAuthoriazation") {
          for {
            response <- api.Middleware
              .withProxyAuthorization("Basic ")
              .apply(Http.succeed(response))
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.proxyAuthorization.getOrElse("error").equals(""))
        },
      ),
    )
}
