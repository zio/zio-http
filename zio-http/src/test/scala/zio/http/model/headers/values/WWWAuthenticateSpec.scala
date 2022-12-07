package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object WWWAuthenticateSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("WWAuthenticate suite")(
    test("should properly parse WWWAuthenticate Basic header") {
      val header = WWWAuthenticate.Basic("realm")
      val parsed = WWWAuthenticate.toWWWAuthenticate("Basic realm=\"realm\"")
      assertTrue(parsed == header)
    },
    test("should properly parse WWWAuthenticate Bearer header") {
      val header = WWWAuthenticate.Bearer("realm", Some("scope"), Some("error"), Some("errorDescription"))
      val parsed = WWWAuthenticate.toWWWAuthenticate(
        "Bearer realm=\"realm\", scope=\"scope\", error=\"error\", error_description=\"errorDescription\"",
      )
      assertTrue(parsed == header)
    },
    test("should properly parse WWWAuthenticate Bearer header") {
      val header = WWWAuthenticate.Bearer("realm", Some("scope"), None, None)
      val parsed = WWWAuthenticate.toWWWAuthenticate(
        "Bearer realm=\"realm\", scope=\"scope\"",
      )
      assertTrue(parsed == header)
    },
    test("should properly parse WWW Authenticate Digest header") {
      val header = WWWAuthenticate.Digest(
        Some("http-auth@example.org"),
        None,
        Some("7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v"),
        Some("FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"),
        Some(true),
        Some("SHA-256"),
        Some("auth, auth-int"),
        Some("UTF-8"),
        Some(true),
      )
      val parsed = WWWAuthenticate.toWWWAuthenticate(
        "Digest realm=\"http-auth@example.org\", nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\", stale=true, algorithm=SHA-256, qop=\"auth, auth-int\", charset=UTF-8, userhash=true",
      )
      assertTrue(parsed == header)
    },
    test("should properly parse WWWAuthenticate Hoba header") {
      val header = WWWAuthenticate.HOBA(Some("realm"), "challenge", 10)
      val parsed = WWWAuthenticate.toWWWAuthenticate(
        "Hoba realm=\"realm\", challenge=\"challenge\", max_age=10",
      )
      assertTrue(parsed == header)
    },
    test("should properly parse WWWAuthenticate Negotiate header") {
      val header = WWWAuthenticate.Negotiate(Some("749efa7b23409c20b92356"))
      val parsed = WWWAuthenticate.toWWWAuthenticate(
        "Negotiate 749efa7b23409c20b92356",
      )
      assertTrue(parsed == header)
    },
    test("should properly render WWW Authenticate Digest header") {
      val header   = WWWAuthenticate.Digest(
        Some("http-auth@example.org"),
        None,
        Some("7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v"),
        Some("FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"),
        Some(true),
        Some("SHA-256"),
        Some("auth, auth-int"),
        Some("UTF-8"),
        Some(true),
      )
      val rendered = WWWAuthenticate.fromWWWAuthenticate(header)
      assertTrue(
        rendered == "Digest realm=\"http-auth@example.org\", nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\", stale=true, algorithm=SHA-256, qop=\"auth, auth-int\", charset=UTF-8, userhash=true",
      )

    },
    test("should properly render WWWAuthenticate Hoba header") {
      val header   = WWWAuthenticate.HOBA(Some("realm"), "challenge", 10)
      val rendered = WWWAuthenticate.fromWWWAuthenticate(header)
      assertTrue(
        rendered == "HOBA realm=\"realm\", challenge=\"challenge\", max_age=10",
      )
    },
    test("should properly render WWWAuthenticate Negotiate header") {
      val header   = WWWAuthenticate.Negotiate(Some("749efa7b23409c20b92356"))
      val rendered = WWWAuthenticate.fromWWWAuthenticate(header)
      assertTrue(
        rendered == "Negotiate 749efa7b23409c20b92356",
      )
    },
  )
}
