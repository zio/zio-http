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

import java.util.UUID

import zio.Chunk
import zio.test.Assertion._
import zio.test._

import zio.http.internal.HttpGen

object URLSpec extends ZIOHttpSpec {
  def extractPath(url: URL): Path = url.path
  def asURL(string: String): URL  = URL.decode(string).toOption.get

  def spec =
    suite("URL")(
      test("empty") {
        check(HttpGen.url) { url =>
          assertTrue(
            url == url ++ URL.empty,
            url == URL.empty ++ url,
          )
        }
      },
      suite("equality/hash")(
        test("leading slash invariant") {
          check(HttpGen.url) { url =>
            val url1 = url.copy(path = extractPath(url).dropLeadingSlash)
            val url2 = url.copy(path = extractPath(url).addLeadingSlash)

            assertTrue(url1 == url2, url1.hashCode == url2.hashCode)
          }
        },
      ),
      suite("normalize")(
        test("adds leading slash") {
          val url = URL(Path("a/b/c"), URL.Location.Absolute(Scheme.HTTP, "abc.com", Some(80)), QueryParams.empty, None)

          val url2 = url.normalize

          assertTrue(url2.encode == "http://abc.com:80/a/b/c", extractPath(url2) == Path("/a/b/c"))
        },
        test("deletes leading slash if there are no path segments") {
          val url  = URL(Path.root, URL.Location.Absolute(Scheme.HTTP, "abc.com", Some(80)), QueryParams.empty, None)
          val url2 = url.normalize

          assertTrue(extractPath(url2) == Path.empty)
        },
        test("add leading slash to absolute URLs without port") {
          val url  = URL(Path("a/b/c"), URL.Location.Absolute(Scheme.HTTP, "abc.com", None), QueryParams.empty, None)
          val url2 = url.normalize
          val urlInterpolator         = url"https://abc.com/a/b/c"
          val urlInterpolator2        = urlInterpolator.normalize
          val baseUrlInterpolator     = url"https://abc.com"
          val extendedUrlInterpolator = (baseUrlInterpolator / "a" / "b" / "c").normalize

          assertTrue(
            url2.encode == "http://abc.com/a/b/c",
            extractPath(url2) == Path("/a/b/c"),
            urlInterpolator2.encode == "https://abc.com/a/b/c",
            extractPath(urlInterpolator2) == Path("/a/b/c"),
            extendedUrlInterpolator.encode == "https://abc.com/a/b/c",
            extractPath(extendedUrlInterpolator) == Path("/a/b/c"),
          )
        },
      ),
      suite("encode-decode symmetry")(
        test("url interpolator") {
          val url           = url"https://api.com:8080/users?x=10&y=20"
          val urlSpaces     = url"https://api.com:8080/users?x=10&y=20&spaces=with%20spaces"
          val urlSpacesPlus = url"https://api.com:8080/users?x=10&y=20&spaces=with+spaces"
          assertTrue(
            url.encode == "https://api.com:8080/users?x=10&y=20",
            urlSpaces.encode == "https://api.com:8080/users?x=10&y=20&spaces=with+spaces",
            urlSpacesPlus.encode == "https://api.com:8080/users?x=10&y=20&spaces=with+spaces",
            URL.decode(url.encode) == Right(url),
            URL.decode(urlSpaces.encode) == Right(urlSpaces),
            URL.decode(urlSpacesPlus.encode) == Right(urlSpacesPlus),
          )
        },
        test("auto-gen") {
          check(HttpGen.url) { url =>
            val expected        = url.copy(path = url.path.addLeadingSlash)
            val expectedEncoded = expected.encode
            val actual          = URL.decode(expected.encode)
            val actualEncoded   = actual.map(_.encode)

            assertTrue(asURL(actualEncoded.toOption.get) == asURL(expectedEncoded), actual.toOption.get == expected)
          }
        },
        test("manual") {
          val urls = Gen.fromIterable(
            Seq(
              "",
              "/",
              "/users?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
              "/users",
              "/users#the%20hash",
              "http://abc.com",
              "http://abc.com/",
              "http://abc.com/list",
              "http://abc.com/users?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
              "http://abc.com/users?u=1&u=2&ord=ASC&txt=scala%20is%20awesome%21",
              "http://abc.com/users?u=1#the%20hash",
              "http://abc.com/users",
              "http://abc.com/users/?u=1&u=2&ord=ASC&txt=scala%20is%20awesome%21",
              "http://abc.com/users#the%20hash",
              "ws://abc.com/subscriptions",
              "wss://abc.com/subscriptions",
            ),
          )

          checkAll(urls) { url =>
            val decoded = URL.decode(url)
            val encoded = decoded.map(_.encode)
            assertTrue(encoded == Right(url) || encoded == Right(url.replaceAll("%20", "+")))
          }
        },
      ),
      suite("fromString")(
        test("should Handle invalid url String with restricted chars") {
          val actual = URL.decode("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg")
          assert(actual)(isLeft)
        },
      ),
      suite("relative")(
        test("converts an url to a relative url") {
          val actual   = URL.decode("http://abc.com/users?a=1&b=2").map(_.relative.encode)
          val expected = Right("/users?a=1&b=2")
          assertTrue(actual == expected)
        },
        test("relative root") {
          val actual   = URL(Path.root, URL.Location.Relative)
          val expected = "/"
          assertTrue(actual.encode == expected)
        },
        test("relative empty") {
          val actual   = URL(Path.empty, URL.Location.Relative)
          val expected = ""
          assertTrue(actual.encode == expected)
        },
      ),
      suite("path")(
        test("updates the path without needed to know the host") {
          val host     = "http://abc.com"
          val channels = "/channels"
          val users    = "/users"
          val actual   = URL.decode(host + users).map(_.path(channels).encode)
          val expected = Right(host + channels)
          assertTrue(actual == expected)
        },
      ),
      suite("builder")(
        test("creates a URL with all attributes set") {
          val builderUrl = URL.empty
            .host("www.abc.com")
            .path("/list")
            .port(8080)
            .scheme(Scheme.HTTPS)
            .setQueryParams("?type=builder&query=provided")

          assertTrue(builderUrl == asURL("https://www.abc.com:8080/list?type=builder&query=provided"))
        },
        test("returns relative URL if port, host, and scheme are not set") {
          val actual = URL.empty
            .path(Path.decode("/list"))
            .setQueryParams(QueryParams("query" -> Chunk("provided"), "type" -> Chunk("builder")))
            .encode

          assertTrue(asURL(actual) == asURL("/list?query=provided&type=builder"))
        },
      ),
      suite("java interop")(
        test("can not create a java.net.URL from a relative URL") {
          check(HttpGen.genRelativeURL) { url =>
            assert(url.toJavaURL)(isNone)
          }
        },
        test("converts a zio.http.URL to java.net.URI") {
          check(HttpGen.genAbsoluteURL) { url =>
            val httpURLString = url.encode
            val javaURLString = url.toJavaURI.toString
            assertTrue(httpURLString == javaURLString)
          }
        },
      ),
      suite("hostPort")(
        test("does not add the port 80 for http") {
          assertTrue(
            URL.decode("http://localhost:80").toOption.flatMap(_.hostPort) == Some("localhost"),
          )
        },
        test("adds the port 8080 for http") {
          assertTrue(
            URL.decode("http://localhost:8080").toOption.flatMap(_.hostPort) == Some("localhost:8080"),
          )
        },
        test("does not add the port 443 for https") {
          assertTrue(
            URL.decode("https://localhost:443").toOption.flatMap(_.hostPort) == Some("localhost"),
          )
        },
        test("adds the port 80 for https") {
          assertTrue(
            URL.decode("https://localhost:80").toOption.flatMap(_.hostPort) == Some("localhost:80"),
          )
        },
      ),
      suite("string interpolator")(
        test("valid static absolute url") {
          val url = url"https://api.com:8080/users?x=10&y=20"
          assertTrue(
            url == URL.decode("https://api.com:8080/users?x=10&y=20").toOption.get,
          )
        },
        test("valid static relative url") {
          val url = url"/users?x=10&y=20"
          assertTrue(
            url == URL.decode("/users?x=10&y=20").toOption.get,
          )
        },
        test("invalid url") {
          val result = typeCheck {
            """val url: URL = url"http:/x/y/z"
            """
          }
          assertZIO(result)(isLeft)
        },
        test("dynamic absolute url") {
          val host   = "localhost"
          val port   = 8080
          val entity = "users"
          val url    = url"http://$host:$port/$entity/get"
          assertTrue(
            url == URL.decode(s"http://localhost:8080/users/get").toOption.get,
          )
        },
        test("dynamic relative url") {
          val entity = "users"
          val uuid   = UUID.fromString("1E7E4039-66AE-4CFA-A493-0AC0FC0AD45B")
          val bool   = false
          val url    = url"$entity/$uuid/get?valid=$bool"
          assertTrue(
            url == URL.decode(s"users/1e7e4039-66ae-4cfa-a493-0ac0fc0ad45b/get?valid=false").toOption.get,
          )
        },
        test("dynamic invalid url") {
          val result = typeCheck {
            """val a = "hello"
               val b = 10
               val url: URL = url"http:/$a:$b/y/z"
            """
          }
          assertZIO(result)(isLeft)
        },
        test("dynamic invalid url 2") {
          val result = typeCheck {
            """val a = "hello"
               val b = false
               val url: URL = url"http://$a:$b/y/z"
            """
          }
          assertZIO(result)(isLeft)
        },
      ),
      suite("relative resolution")(
        // next ones are edge cases
        test("absolute reference with relative base") {
          val base      = url"base/relative#basefrag"
          val reference = url"https://reference/ignored/.././absolute#reffrag"

          // uses reference without dot segments
          val expected = url"https://reference/absolute#reffrag"

          val result = base.resolve(reference)
          assertTrue(result.contains(expected))
        },
        test("absolute reference with absolute base") {
          val base      = url"https://base#basefrag"
          val reference = url"https://reference/ignored/.././absolute#reffrag"

          // uses reference without dot segments
          val expected = url"https://reference/absolute#reffrag"

          val result = base.resolve(reference)
          assertTrue(result.contains(expected))
        },
        test("relative reference with relative base") {
          val base      = url"base/relative"
          val reference = url"reference/relative"

          val result = base.resolve(reference)
          assertTrue(result.isLeft)
        },

        // remainder are main resolution logic - absolute base, relative reference
        test("empty reference path without query params") {
          val base      = url"https://base/./ignored/../absolute?param=base#basefrag"
          val reference = url"#reffrag"

          // uses unmodified base path and base query params
          val expected = url"https://base/./ignored/../absolute?param=base#reffrag"

          val result = base.resolve(reference).toOption.get
          assertTrue(result == expected)
        },
        test("empty reference path with query params") {
          val base      = url"https://base/./ignored/../absolute?param=base#basefrag"
          val reference = url"?param=reference#reffrag"

          // uses unmodified base path and reference query params
          val expected = url"https://base/./ignored/../absolute?param=reference#reffrag"

          val result = base.resolve(reference).toOption.get
          assertTrue(result == expected)
        },
        test("non-empty reference path with a leading slash") {
          val base      = url"https://base/./ignored/../first/second?param=base#basefrag"
          val reference = url"/reference/./ignored/../last?param=reference#reffrag"

          // uses reference path without dot segments and reference query params
          val expected = url"https://base/reference/last?param=reference#reffrag"

          val result = base.resolve(reference)
          assertTrue(result.contains(expected))
        },
        test("non empty reference path without a leading slash") {
          val base      = url"https://base/./ignored/../first/..?param=base#basefrag"
          val reference = url"reference/./ignored/../last?param=reference#reffrag"

          // uses base path without last segment, reference segments appended, without dot segments, and reference query params
          val expected = url"https://base/reference/last?param=reference#reffrag"

          val result = base.resolve(reference).toOption.get
          assertTrue(result == expected)
        },
        test("non-empty reference path without a leading slash and empty base path") {
          val base      = url"https://base?param=base#basefrag"
          val reference = url"reference/./ignored/../last?param=reference#reffrag"

          // uses reference path without dot segments and a leading slash
          val expected = url"https://base/reference/last?param=reference#reffrag"

          val result = base.resolve(reference)
          assertTrue(result.contains(expected))
        },
      ),
    )
}
