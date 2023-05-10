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

import scala.collection.Seq

import zio.test._

import zio.http.Path.Segment
import zio.http.internal.HttpGen

object PathSpec extends ZIOSpecDefault with ExitAssertion {
  val a = "a"
  val b = "b"
  val c = "c"

  def collect[A](pf: PartialFunction[Path, A]): String => Option[A] = path => pf.lift(Path.decode(path))

  def spec = suite("path")(
    suite("collect")(
      test("/") {
        val gen = Gen.fromIterable(
          Seq(
            // Exact
            collect { case Root => true }                   -> "/",
            collect { case Root / "a" => true }             -> "/a",
            collect { case Root / "a" / "b" => true }       -> "/a/b",
            collect { case Root / "a" / "b" / "c" => true } -> "/a/b/c",

            // Wildcards
            collect { case Root / _ => true }         -> "/a",
            collect { case Root / _ / _ => true }     -> "/a/b",
            collect { case Root / _ / _ / _ => true } -> "/a/b/c",

            // Wildcard mix
            collect { case _ / "c" => true }     -> "/a/b/c",
            collect { case _ / _ / "c" => true } -> "/a/b/c",

            // Trailing Slash
            collect { case ~~ / "a" / "" => true }             -> "a/",
            collect { case ~~ / "a" / "b" / "" => true }       -> "a/b/",
            collect { case ~~ / "a" / "b" / "c" / "" => true } -> "a/b/c/",
          ),
        )

        checkAll(gen) { case (pf, path) =>
          assertTrue(pf(path).isDefined)
        }
      },
      test("/:") {
        val gen = Gen.fromIterable(
          Seq(
            // Exact
            collect { case "a" /: Root => true }               -> "a/",
            collect { case "a" /: "b" /: Root => true }        -> "a/b/",
            collect { case "a" /: "b" /: "c" /: Root => true } -> "a/b/c/",

            // Wildcard
            collect { case "a" /: _ => true }        -> "a",
            collect { case "a" /: "b" /: _ => true } -> "a/b/c",
            collect { case "a" /: _ /: _ => true }   -> "a/b/c",
            collect { case "a" /: _ => true }        -> "a/b/c",

            //
            collect { case "a" /: "b" /: "c" /: _ => true } -> "a/b/c",
            collect { case "a" /: "b" /: _ /: _ => true }   -> "a/b/c",
            collect { case "a" /: _ /: _ /: _ => true }     -> "a/b/c",
            collect { case _ /: _ /: _ /: _ => true }       -> "/a/b/c",
            collect { case _ /: _ /: _ => true }            -> "/a/b/c",
            collect { case _ /: _ => true }                 -> "/a/b/c",

            // Trailing slash
            collect { case "a" /: Root => true }               -> "a/",
            collect { case "a" /: "b" /: Root => true }        -> "a/b/",
            collect { case "a" /: "b" /: "c" /: Root => true } -> "a/b/c/",
            collect { case "a" /: Root => true }               -> "a/",

            // Leading Slash
            collect { case "" /: ~~ => true }                      -> "/",
            collect { case "" /: "a" /: ~~ => true }               -> "/a",
            collect { case "" /: "a" /: "b" /: ~~ => true }        -> "/a/b",
            collect { case "" /: "a" /: "b" /: "c" /: ~~ => true } -> "/a/b/c",
          ),
        )

        checkAll(gen) { case (pf, path) =>
          assertTrue(pf(path).isDefined)
        }
      },
    ),
    suite("decode")(
      test("segments") {
        // Internal representation of a path
        val paths = Gen.fromIterable(
          Seq(
            "/"       -> Root                  -> Vector(Segment.root),
            "/a"      -> Root / a              -> Vector(Segment.root, Segment(a)),
            "/a/b"    -> Root / a / b          -> Vector(Segment.root, Segment(a), Segment(b)),
            "/a/b/c"  -> Root / a / b / c      -> Vector(Segment.root, Segment(a), Segment(b), Segment(c)),
            "a/b/c"   -> ~~ / a / b / c      -> Vector(Segment(a), Segment(b), Segment(c)),
            "a/b"     -> ~~ / a / b          -> Vector(Segment(a), Segment(b)),
            "a"       -> ~~ / a              -> Vector(Segment(a)),
            ""        -> ~~                  -> Vector(),
            "a/"      -> ~~ / a / ""         -> Vector(Segment(a), Segment.root),
            "a/b/"    -> ~~ / a / b / ""     -> Vector(Segment(a), Segment(b), Segment.root),
            "a/b/c/"  -> ~~ / a / b / c / "" -> Vector(Segment(a), Segment(b), Segment(c), Segment.root),
            "/a/b/c/" -> Root / a / b / c / "" -> Vector(Segment.root, Segment(a), Segment(b), Segment(c), Segment.root),
            "/a/b/"   -> Root / a / b / ""     -> Vector(Segment.root, Segment(a), Segment(b), Segment.root),
            "/a/"     -> Root / a / ""         -> Vector(Segment.root, Segment(a), Segment.root),
          ),
        )
        checkAll(paths) { case ((encoded, path), segments) =>
          val decoded = Path.decode(encoded)

          assertTrue(
            decoded.segments == segments,
            path.segments == segments,
            path.encode == encoded,
            decoded.encode == encoded,
          )

        }
      },
      test("multiple leading slashes") {
        val encoded = "///a/b/c"
        val decoded = Path.decode(encoded)
        assertTrue(decoded == Root / a / b / c)
      },
      test("multiple trailing slashes") {
        val encoded = "a/b/c///"
        val decoded = Path.decode(encoded)
        assertTrue(decoded == ~~ / a / b / c / "")
      },
    ),
    suite("append") {
      test("simplifies internal representation") {
        val urls = Gen.fromIterable(
          Seq(
            Root / ""                    -> Root,
            Root / "" / a / "" / "" / "" -> Root / a / "",
            ~~ / ""                    -> Root,
            ~~ / "" / a / ""           -> Root / a / "",
          ),
        )
        checkAll(urls) { case (actual, expected) => assertTrue(actual == expected) }
      }
    },
    suite("prepend")(
      test("simplifies internal representation") {
        val urls = Gen.fromIterable(
          Seq(
            "" /: Root                                  -> Root,
            a /: Root                                   -> ~~ / a / "",
            "" /: a /: ~~                             -> Root / a,
            "" /: a /: b /: ~~                        -> Root / a / b,
            "" /: a /: b /: c /: ~~                   -> Root / a / b / c,
            "" /: a /: b /: c /: Root                   -> Root / a / b / c / "",
            "" /: a /: "" /: b /: "" /: Root            -> Root / a / b / "",
            a /: "" /: "" /: b /: "" /: "" /: c /: Root -> ~~ / a / b / c / "",
          ),
        )
        checkAll(urls) { case (actual, expected) => assertTrue(actual == expected) }
      },
    ),
    suite("startsWith")(
      test("isTrue") {
        val gen = Gen.fromIterable(
          Seq(
            Root                   -> Root,
            Root / "a"             -> Root / "a",
            Root / "a" / "b"       -> Root / "a" / "b",
            Root / "a" / "b" / "c" -> Root / "a",
            Root / "a" / "b" / "c" -> Root / "a" / "b" / "c",
            Root / "a" / "b" / "c" -> Root / "a" / "b" / "c",
            Root / "a" / "b" / "c" -> Root / "a" / "b" / "c",
          ),
        )

        checkAll(gen) { case (path, expected) =>
          val actual = path.startsWith(expected)
          assertTrue(actual)
        }
      },
      test("isFalse") {
        val gen = Gen.fromIterable(
          Seq(
            Root             -> Root / "a",
            Root / "a"       -> Root / "a" / "b",
            Root / "a"       -> Root / "b",
            Root / "a" / "b" -> Root / "a" / "b" / "c",
          ),
        )

        checkAll(gen) { case (path, expected) =>
          val actual = !path.startsWith(expected)
          assertTrue(actual)
        }
      },
    ),
    test("take") {
      val gen = Gen.fromIterable(
        Seq(
          (1, Root)                   -> Root,
          (1, Root / "a")             -> Root,
          (1, Root / "a" / "b")       -> Root,
          (1, Root / "a" / "b" / "c") -> Root,
          (2, Root / "a" / "b" / "c") -> Root / "a",
          (3, Root / "a" / "b" / "c") -> Root / "a" / "b",
          (4, Root / "a" / "b" / "c") -> Root / "a" / "b" / "c",
        ),
      )

      checkAll(gen) { case ((n, path), expected) =>
        val actual = path.take(n)
        assertTrue(actual == expected)
      }
    },
    test("drop") {
      val gen = Gen.fromIterable(
        Seq(
          (1, Root)                   -> ~~,
          (1, Root / "a")             -> ~~ / "a",
          (1, Root / "a" / "b")       -> ~~ / "a" / "b",
          (1, Root / "a" / "b" / "c") -> ~~ / "a" / "b" / "c",
          (2, Root / "a" / "b" / "c") -> ~~ / "b" / "c",
          (3, Root / "a" / "b" / "c") -> ~~ / "c",
          (4, Root / "a" / "b" / "c") -> ~~,
        ),
      )

      checkAll(gen) { case ((n, path), expected) =>
        val actual = path.drop(n)
        assertTrue(actual == expected)
      }
    },
    test("dropLast") {
      val gen = Gen.fromIterable(
        Seq(
          (1, Root)                   -> ~~,
          (1, Root / "a")             -> Root,
          (1, Root / "a" / "b")       -> Root / "a",
          (1, Root / "a" / "b" / "c") -> Root / "a" / "b",
          (2, Root / "a" / "b" / "c") -> Root / "a",
          (3, Root / "a" / "b" / "c") -> Root,
          (4, Root / "a" / "b" / "c") -> ~~,
        ),
      )

      checkAll(gen) { case ((n, path), expected) =>
        val actual = path.dropLast(n)
        assertTrue(actual == expected)
      }
    },
    suite("extractor")(
      suite("int()")(
        test("extract path 'user' /: int(1)") {
          val path = collect { case "" /: "user" /: int(age) /: ~~ => age }
          assertTrue(path("/user/1").contains(1))
        },
        test("extract path 'user' /: int(Xyz)") {
          val path = collect { case "" /: "user" /: int(age) /: ~~ => age }
          assertTrue(path("/user/Xyz").isEmpty)
        },
      ),
      suite("boolean()")(
        test("extract path 'user' /: boolean(true)") {
          val path = collect { case "user" /: boolean(ok) /: ~~ => ok }
          assertTrue(path("user/True").contains(true))
        },
        test("extract path 'user' /: boolean(false)") {
          val path = collect { case "user" /: boolean(ok) /: ~~ => ok }
          assertTrue(path("user/false").contains(false))
        },
      ),
    ),
    suite("addTrailingSlash")(
      test("always ends with a root") {
        check(HttpGen.anyPath) { path =>
          val actual   = path.addTrailingSlash.segments.lastOption
          val expected = Some(Segment.root)
          assertTrue(actual == expected)
        }
      },
    ),
    suite("dropTrailingSlash")(
      test("never ends with a root") {
        check(HttpGen.anyPath) { path =>
          val actual     = path.dropTrailingSlash.segments.lastOption
          val unexpected = Some(Segment.root)
          assertTrue(actual != unexpected)
        }
      },
    ),
  )
}
