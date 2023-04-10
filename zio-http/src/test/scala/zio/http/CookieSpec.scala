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

package zio.http

import zio._
import zio.test.Assertion.{equalTo, isLeft, isRight, startsWithString}
import zio.test._

import zio.http.Cookie.SameSite

object CookieSpec extends ZIOSpecDefault {

  override def spec =
    suite("CookieSpec")(
      suite("getter")(
        test("request") {
          val cookieGen = for {
            name    <- Gen.alphaNumericString
            content <- Gen.alphaNumericString
          } yield (name, content) -> Cookie.Response(name, content)
          check(cookieGen) { case ((name, content), cookie) =>
            assertTrue(cookie.content == content) && assertTrue(cookie.name == name)
          }
        },
        test("response") {
          val responseCookieGen = for {
            name       <- Gen.alphaNumericString
            content    <- Gen.alphaNumericString
            domain     <- Gen.option(Gen.alphaNumericString)
            path       <- Gen.option(Gen.elements(Path.root / "a", Path.root / "a" / "b"))
            isSecure   <- Gen.boolean
            isHttpOnly <- Gen.boolean
            maxAge     <- Gen.option(Gen.long.map(java.time.Duration.ofSeconds(_)))
            sameSite   <- Gen.option(Gen.fromIterable(Cookie.SameSite.values))
          } yield (name, content) -> Cookie
            .Response(name, content, domain, path, isSecure, isHttpOnly, maxAge, sameSite)

          check(responseCookieGen) { case ((name, content), cookie) =>
            assertTrue(cookie.content == content) &&
            assertTrue(cookie.name == name) &&
            assertTrue(cookie.domain == cookie.domain) &&
            assertTrue(cookie.path == cookie.path) &&
            assertTrue(cookie.maxAge == cookie.maxAge) &&
            assertTrue(cookie.sameSite == cookie.sameSite) &&
            assertTrue(cookie.isSecure == cookie.isSecure) &&
            assertTrue(cookie.isHttpOnly == cookie.isHttpOnly)
          }
        },
      ),
      suite("encode")(
        test("request") {
          val cookie    = Cookie.Response("name", "value")
          val cookieGen = Gen.fromIterable(
            Seq(
              cookie                      -> "name=value",
              cookie.withContent("other") -> "name=other",
              cookie.withName("name1")    -> "name1=value",
            ),
          )
          checkAll(cookieGen) { case (cookie, expected) => assertTrue(cookie.encode == Right(expected)) }
        },
        test("response") {
          val cookie = Cookie.Response("name", "content")

          val cookieGen: Gen[Any, (Cookie.Response, Assertion[String])] = Gen.fromIterable(
            Seq(
              cookie                                     -> equalTo("name=content"),
              cookie.copy(domain = Some("abc.com"))      -> equalTo("name=content; Domain=abc.com"),
              cookie.copy(isHttpOnly = true)             -> equalTo("name=content; HTTPOnly"),
              cookie.copy(path = Some(Path.root / "a"))  -> equalTo("name=content; Path=/a"),
              cookie.copy(sameSite = Some(SameSite.Lax)) -> equalTo("name=content; SameSite=Lax"),
              cookie.copy(isSecure = true)               -> equalTo("name=content; Secure"),
              cookie.copy(maxAge = Some(1 day))          -> startsWithString("name=content; Max-Age=86400; Expires="),
            ),
          )

          checkAll(cookieGen) { case (cookie, assertion) => assert(cookie.encode)(isRight(assertion)) }
        },
        test("invalid encode") {
          val cookie = Cookie.Response("1", null)
          assert(cookie.encode)(isLeft)
        },
      ),
      suite("decode")(
        test("request") {
          val cookie  = Cookie.Request("name", "value")
          val program = cookie.encode.flatMap(Cookie.decodeRequest(_))
          assertTrue(program == Right(Chunk(cookie)))
        },
        test("decode response") {
          val responseCookieGen = for {
            name       <- Gen.alphaNumericStringBounded(1, 4)
            content    <- Gen.alphaNumericStringBounded(1, 4)
            domain     <- Gen.option(Gen.alphaNumericStringBounded(1, 4))
            path       <- Gen.option(Gen.elements(Path.root / "a", Path.root / "a" / "b"))
            maxAge     <- Gen.option(Gen.long(1, 86400).map(java.time.Duration.ofSeconds(_)))
            sameSite   <- Gen.option(Gen.fromIterable(Cookie.SameSite.values))
            isSecure   <- Gen.boolean
            isHttpOnly <- Gen.boolean
          } yield Cookie.Response(name, content, domain, path, isSecure, isHttpOnly, maxAge, sameSite)

          check(responseCookieGen) { cookie =>
            val encoded = cookie.encodeValidate(true)
            val decoded = encoded.flatMap(Cookie.decodeResponse(_, true))
            assert(decoded)(isRight(equalTo(cookie)))
          }
        },
      ),
      test("signature") {
        val cookie = Cookie.Response("name", "value")
        val signed = cookie.sign("ABC").toRequest

        assertTrue(signed.unSign("ABC").contains(cookie.toRequest)) &&
        assertTrue(signed.unSign("PQR").isEmpty)
      },
    )
}
