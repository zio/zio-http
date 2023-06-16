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

import zio.Chunk
import zio.test._

import zio.http.internal.HttpGen

object PathSpec extends ZIOSpecDefault with ExitAssertion {
  import Path.Flag

  val a = "a"
  val b = "b"
  val c = "c"

  def collect[A](pf: PartialFunction[Path, A]): String => Option[A] = path => pf.lift(Path.decode(path))

  def expand(path: Path): List[String] =
    path.unapply match {
      case None               => Nil
      case Some((head, tail)) => head :: expand(tail)
    }

  def expandRight(path: Path): List[String] =
    path.unapplyRight match {
      case None               => Nil
      case Some((tail, head)) => expandRight(tail) :+ head
    }

  def spec = suite("path")(
    suite("unapply")(
      test("empty") {
        assertTrue(Path.empty.unapply == None)
      },
      test("root") {
        assertTrue(Path.root.unapply == Some(("", Path.empty)))
      },
      test("other cases") {
        val gen = Gen.fromIterable(
          Seq(
            Root / "a"                   -> List("", "a"),
            Root / "a" / "b"             -> List("", "a", "b"),
            Root / "a" / "b" / "c" / ""  -> List("", "a", "b", "c", ""),
            Empty / "a"                  -> List("a"),
            Empty / "a" / "b"            -> List("a", "b"),
            Empty / "a" / "b" / "c" / "" -> List("a", "b", "c", ""),
          ),
        )

        checkAll(gen) { case (path, expected) =>
          assertTrue(expandRight(path) == expected)
        }
      },
    ),
    suite("unapplyRight")(
      test("empty") {
        assertTrue(Path.empty.unapplyRight == None)
      },
      test("root") {
        assertTrue(Path.root.unapplyRight == Some((Path.empty, "")))
      },
      test("other cases") {
        val gen = Gen.fromIterable(
          Seq(
            Root / "a"                   -> List("a", ""),
            Root / "a" / "b"             -> List("b", "a", ""),
            Root / "a" / "b" / "c" / ""  -> List("", "c", "b", "a", ""),
            Empty / "a"                  -> List("a"),
            Empty / "a" / "b"            -> List("b", "a"),
            Empty / "a" / "b" / "c" / "" -> List("", "c", "b", "a"),
          ),
        )

        checkAll(gen) { case (path, expected) =>
          assertTrue(expand(path).reverse == expected)
        }
      },
    ),
    suite("collect")(
      test("LeadingSlash") {
        val gen = Gen.fromIterable(
          Seq(
            collect { case LeadingSlash(Empty / "a") => true } -> "/a",
          ),
        )

        checkAll(gen) { case (pf, path) =>
          assertTrue(pf(path).isDefined)
        }
      },
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
            collect { case Empty / "a" / "" => true }             -> "a/",
            collect { case Empty / "a" / "b" / "" => true }       -> "a/b/",
            collect { case Empty / "a" / "b" / "c" / "" => true } -> "a/b/c/",
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
            collect { case "" /: Empty => true }                      -> "/",
            collect { case "" /: "a" /: Empty => true }               -> "/a",
            collect { case "" /: "a" /: "b" /: Empty => true }        -> "/a/b",
            collect { case "" /: "a" /: "b" /: "c" /: Empty => true } -> "/a/b/c",
          ),
        )

        checkAll(gen) { case (pf, path) =>
          assertTrue(pf(path).isDefined)
        }
      },
    ),
    suite("decode")(
      test("segments") {
        import Path.Flags

        // Internal representation of a path
        val paths = Gen.fromIterable(
          Seq(
            "/"       -> Root                   -> Path(Flags(Flag.LeadingSlash), Chunk.empty),
            "/a"      -> Root / a               -> Path(Flags(Flag.LeadingSlash), Chunk("a")),
            "/a/b"    -> Root / a / b           -> Path(Flags(Flag.LeadingSlash), Chunk("a", "b")),
            "/a/b/c"  -> Root / a / b / c       -> Path(Flags(Flag.LeadingSlash), Chunk("a", "b", "c")),
            "a/b/c"   -> Empty / a / b / c      -> Path(Flags.none, Chunk("a", "b", "c")),
            "a/b"     -> Empty / a / b          -> Path(Flags.none, Chunk("a", "b")),
            "a"       -> Empty / a              -> Path(Flags.none, Chunk("a")),
            ""        -> Empty                  -> Path(Flags.none, Chunk.empty),
            "a/"      -> Empty / a / ""         -> Path(Flags(Flag.TrailingSlash), Chunk("a")),
            "a/b/"    -> Empty / a / b / ""     -> Path(Flags(Flag.TrailingSlash), Chunk("a", "b")),
            "a/b/c/"  -> Empty / a / b / c / "" -> Path(Flags(Flag.TrailingSlash), Chunk("a", "b", "c")),
            "/a/b/c/" -> Root / a / b / c / ""  -> Path(
              Flags(Flag.LeadingSlash, Flag.TrailingSlash),
              Chunk("a", "b", "c"),
            ),
            "/a/b/"   -> Root / a / b / ""      -> Path(Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk("a", "b")),
            "/a/"     -> Root / a / ""          -> Path(Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk("a")),
          ),
        )
        checkAll(paths) { case ((encoded, path1), path2) =>
          val decoded = Path.decode(encoded)

          assertTrue(decoded == path2) &&
          assertTrue(path1 == path2) &&
          assertTrue(decoded.encode == encoded)
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
        assertTrue(decoded == (Empty / a / b / c).addTrailingSlash)
      },
    ),
    suite("isRoot")(
      test("isTrue") {
        assertTrue(Path(Path.Flags(Flag.LeadingSlash), Chunk.empty).isRoot) &&
        assertTrue(Path(Path.Flags(Flag.TrailingSlash), Chunk.empty).isRoot) &&
        assertTrue(Path(Path.Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty).isRoot)
      },
    ),
    suite("leading / trailing")(
      test("root dropping leading slash becomes empty") {
        assertTrue(Path.root.dropLeadingSlash == Path.empty)
      },
      test("root dropping trailing slash becomes empty") {
        assertTrue(Path.root.dropTrailingSlash == Path.empty)
      },
    ),
    suite("size")(
      test("empty") {
        assertTrue(Path.empty.size == 0)
      },
      test("all roots") {
        val weirdRoot1 = Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)
        val weirdRoot2 = Path(Path.Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty)
        val weirdRoot3 = Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)

        assertTrue(Path.root.size == 1) &&
        assertTrue(weirdRoot1.size == 1) &&
        assertTrue(weirdRoot2.size == 1) &&
        assertTrue(weirdRoot3.size == 1)
      },
      test("hard coded cases") {
        assertTrue(Path("a").size == 1) &&
        assertTrue(Path("/a").size == 2) &&
        assertTrue(Path("a/").size == 2) &&
        assertTrue(Path("/a/").size == 3) &&
        assertTrue(Path("a/b").size == 2) &&
        assertTrue(Path("/a/b").size == 3) &&
        assertTrue(Path("a/b/").size == 3) &&
        assertTrue(Path("/a/b/").size == 4)
      },
      test("all other cases") {
        check(HttpGen.nonEmptyPath) { path =>
          val without = path.dropLeadingSlash.dropTrailingSlash
          val size    = without.size

          assertTrue(size == path.segments.size) &&
          assertTrue(without.addLeadingSlash.size == size + 1) &&
          assertTrue(without.addTrailingSlash.size == size + 1) &&
          assertTrue(without.addLeadingSlash.addTrailingSlash.size == size + 2)
        }
      },
    ),
    suite("normalization")(
      test("simplifies internal representation") {
        val urls = Gen.fromIterable(
          Seq(
            Root                   -> Root.addLeadingSlash,
            Root                   -> Root.addLeadingSlash.addTrailingSlash,
            Empty.addTrailingSlash -> Empty.addTrailingSlash.addTrailingSlash.addTrailingSlash,
          ),
        )
        checkAll(urls) { case (actual, expected) => assertTrue(actual == expected) }
      },
      test("various roots are equivalent") {
        assertTrue(Path.root == Path(Path.Flags(Flag.LeadingSlash), Chunk.empty)) &&
        assertTrue(Path.root == Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)) &&
        assertTrue(Path.root == Path(Path.Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty))
      },
      test("prepending turns a root into a path with a trailing slash") {
        assertTrue("a" /: Root == Path("a/"))
      },
      test("appending turns a root into a path with a leading slash") {
        assertTrue(Root / "a" == Path("/a"))
      },
      test("root is not empty, but empty is") {
        assertTrue(Root.isEmpty == false) &&
        assertTrue(Empty.isEmpty == true)
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
            Empty / "a"      -> Root / "a",
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
          (1, Root)                        -> Root,
          (1, Root / "a")                  -> Root,
          (1, Root / "a" / "b")            -> Root,
          (1, Root / "a" / "b" / "c")      -> Root,
          (2, Root / "a" / "b" / "c")      -> Root / "a",
          (3, Root / "a" / "b" / "c")      -> Root / "a" / "b",
          (4, Root / "a" / "b" / "c")      -> Root / "a" / "b" / "c",
          (1, Root)                        -> Root / "",
          (2, Root / "a" / "")             -> Root / "a",
          (3, Root / "a" / "b" / "")       -> Root / "a" / "b",
          (4, Root / "a" / "b" / "c" / "") -> Root / "a" / "b" / "c",
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
          (1, Root)                   -> Empty,
          (1, Root / "a")             -> Empty / "a",
          (1, Root / "a" / "b")       -> Empty / "a" / "b",
          (1, Root / "a" / "b" / "c") -> Empty / "a" / "b" / "c",
          (2, Root / "a" / "b" / "c") -> Empty / "b" / "c",
          (3, Root / "a" / "b" / "c") -> Empty / "c",
          (4, Root / "a" / "b" / "c") -> Empty,
        ),
      )

      checkAll(gen) { case ((n, path), expected) =>
        val actual = path.drop(n)
        assertTrue(actual == expected)
      }
    },
    test("dropRight") {
      val gen = Gen.fromIterable(
        Seq(
          (1, Root)                   -> Empty,
          (1, Root / "a")             -> Root,
          (1, Root / "a" / "b")       -> Root / "a",
          (1, Root / "a" / "b" / "c") -> Root / "a" / "b",
          (2, Root / "a" / "b" / "c") -> Root / "a",
          (3, Root / "a" / "b" / "c") -> Root,
          (4, Root / "a" / "b" / "c") -> Empty,
          (1, Empty / "a" / "")       -> Empty / "a",
          (1, Root / "a" / "")        -> Root / "a",
          (2, Empty / "a" / "")       -> Empty,
          (2, Root / "a" / "")        -> Root,
          (3, Empty / "a" / "")       -> Empty,
          (3, Root / "a" / "")        -> Empty,
        ),
      )

      checkAll(gen) { case ((n, path), expected) =>
        val actual = path.dropRight(n)
        assertTrue(actual == expected)
      }
    },
    suite("++")(
      test("can add trailing slash") {
        check(HttpGen.nonEmptyPath) { path =>
          val weirdRoot1 = Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)
          val weirdRoot2 = Path(Path.Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty)
          val weirdRoot3 = Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)

          assertTrue((path ++ Root).trailingSlash) &&
          assertTrue((path ++ weirdRoot1).trailingSlash) &&
          assertTrue((path ++ weirdRoot2).trailingSlash) &&
          assertTrue((path ++ weirdRoot3).trailingSlash)
        }
      },
      test("can add leading slash") {
        check(HttpGen.nonEmptyPath) { path =>
          val weirdRoot1 = Path(Path.Flags(Flag.LeadingSlash), Chunk.empty)
          val weirdRoot2 = Path(Path.Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty)
          val weirdRoot3 = Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)

          assertTrue((Root ++ path).leadingSlash) &&
          assertTrue((weirdRoot1 ++ path).leadingSlash) &&
          assertTrue((weirdRoot2 ++ path).leadingSlash) &&
          assertTrue((weirdRoot3 ++ path).leadingSlash)
        }
      },
      test("empty is right identity") {
        check(HttpGen.anyPath) { path =>
          assertTrue(path ++ Path.empty == path)
        }
      },
      test("empty is left identity") {
        check(HttpGen.anyPath) { path =>
          assertTrue(Path.empty ++ path == path)
        }
      },
      test("root is left identity for right path with root") {
        check(HttpGen.anyPath) { path =>
          val path2 = path.addLeadingSlash

          assertTrue(Path.root ++ path2 == path2)
        }
      },
    ),
    suite("extractor")(
      suite("int()")(
        test("extract path 'user' /: int(1)") {
          val path = collect { case "" /: "user" /: int(age) /: Empty => age }
          assertTrue(path("/user/1").contains(1))
        },
        test("extract path 'user' /: int(Xyz)") {
          val path = collect { case "" /: "user" /: int(age) /: Empty => age }
          assertTrue(path("/user/Xyz").isEmpty)
        },
      ),
      suite("boolean()")(
        test("extract path 'user' /: boolean(true)") {
          val path = collect { case "user" /: boolean(ok) /: Empty => ok }
          assertTrue(path("user/True").contains(true))
        },
        test("extract path 'user' /: boolean(false)") {
          val path = collect { case "user" /: boolean(ok) /: Empty => ok }
          assertTrue(path("user/false").contains(false))
        },
      ),
    ),
    suite("addTrailingSlash")(
      test("always ends with a root") {
        check(HttpGen.anyPath) { path =>
          val actual = path.addTrailingSlash.flags
          assertTrue(Path.Flag.TrailingSlash.check(actual) == true)
        }
      },
    ),
    suite("dropTrailingSlash")(
      test("never ends with a root") {
        check(HttpGen.anyPath) { path =>
          val actual = path.dropTrailingSlash.flags
          assertTrue(Path.Flag.TrailingSlash.check(actual) == false)
        }
      },
    ),
    suite("addLeadingSlash")(
      test("always starts with a root") {
        check(HttpGen.anyPath) { path =>
          val actual = path.addLeadingSlash.flags
          assertTrue(Path.Flag.LeadingSlash.check(actual) == true)
        }
      },
    ),
    suite("dropLeadingSlash")(
      test("never starts with a root") {
        check(HttpGen.anyPath) { path =>
          val actual = path.dropLeadingSlash.flags
          assertTrue(Path.Flag.LeadingSlash.check(actual) == false)
        }
      },
    ),
  )
}
