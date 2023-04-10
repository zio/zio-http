/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.headers

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.Header.WWWAuthenticate

object WWWAuthenticateSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("WWAuthenticate suite")(
    test("should properly parse WWWAuthenticate Basic header") {
      val header = WWWAuthenticate.Basic(Some("realm"))
      val parsed = WWWAuthenticate.parse("Basic realm=\"realm\"")
      assertTrue(parsed == Right(header))
    },
    test("should properly parse WWWAuthenticate Bearer header") {
      val header = WWWAuthenticate.Bearer("realm", Some("scope"), Some("error"), Some("errorDescription"))
      val parsed = WWWAuthenticate.parse(
        "Bearer realm=\"realm\", scope=\"scope\", error=\"error\", error_description=\"errorDescription\"",
      )
      assertTrue(parsed == Right(header))
    },
    test("should properly parse WWWAuthenticate Bearer header") {
      val header = WWWAuthenticate.Bearer("realm", Some("scope"), None, None)
      val parsed = WWWAuthenticate.parse(
        "Bearer realm=\"realm\", scope=\"scope\"",
      )
      assertTrue(parsed == Right(header))
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
      val parsed = WWWAuthenticate.parse(
        "Digest realm=\"http-auth@example.org\", nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\", stale=true, algorithm=SHA-256, qop=\"auth, auth-int\", charset=UTF-8, userhash=true",
      )
      assertTrue(parsed == Right(header))
    },
    test("should properly parse WWWAuthenticate Hoba header") {
      val header = WWWAuthenticate.HOBA(Some("realm"), "challenge", 10)
      val parsed = WWWAuthenticate.parse(
        "Hoba realm=\"realm\", challenge=\"challenge\", max_age=10",
      )
      assertTrue(parsed == Right(header))
    },
    test("should properly parse WWWAuthenticate Negotiate header") {
      val header = WWWAuthenticate.Negotiate(Some("749efa7b23409c20b92356"))
      val parsed = WWWAuthenticate.parse(
        "Negotiate 749efa7b23409c20b92356",
      )
      assertTrue(parsed == Right(header))
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
      val rendered = WWWAuthenticate.render(header)
      assertTrue(
        rendered == "Digest realm=\"http-auth@example.org\", nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\", stale=true, algorithm=SHA-256, qop=\"auth, auth-int\", charset=UTF-8, userhash=true",
      )

    },
    test("should properly render WWWAuthenticate Hoba header") {
      val header   = WWWAuthenticate.HOBA(Some("realm"), "challenge", 10)
      val rendered = WWWAuthenticate.render(header)
      assertTrue(
        rendered == "HOBA realm=\"realm\", challenge=\"challenge\", max_age=10",
      )
    },
    test("should properly render WWWAuthenticate Negotiate header") {
      val header   = WWWAuthenticate.Negotiate(Some("749efa7b23409c20b92356"))
      val rendered = WWWAuthenticate.render(header)
      assertTrue(
        rendered == "Negotiate 749efa7b23409c20b92356",
      )
    },
  )
}
