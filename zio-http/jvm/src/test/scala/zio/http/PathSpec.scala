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

object PathSpec extends ZIOHttpSpec with ExitAssertion {
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
    suite("unnest")(
      test("any path unnest empty is unchanged") {
        check(HttpGen.nonEmptyPath) { path =>
          assertTrue(path.unnest(Path.empty) == path)
        }
      },
      test("any path with leading slash unnest root no longer has leading slash") {
        check(HttpGen.nonEmptyPath) { path0 =>
          val path = path0.dropLeadingSlash

          assertTrue(path.addLeadingSlash.unnest(Path.root) == path)
        }
      },
      test("general unnest") {
        check(HttpGen.nonEmptyPath, Gen.int(0, 3)) { case (path, n) =>
          val prefix   = path.take(n)
          val leftover = path.drop(n)

          assertTrue(path.unnest(prefix) == leftover)
        }
      },
    ),
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
            Path.root / "a"                   -> List("", "a"),
            Path.root / "a" / "b"             -> List("", "a", "b"),
            Path.root / "a" / "b" / "c" / ""  -> List("", "a", "b", "c", ""),
            Path.empty / "a"                  -> List("a"),
            Path.empty / "a" / "b"            -> List("a", "b"),
            Path.empty / "a" / "b" / "c" / "" -> List("a", "b", "c", ""),
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
            Path.root / "a"                   -> List("a", ""),
            Path.root / "a" / "b"             -> List("b", "a", ""),
            Path.root / "a" / "b" / "c" / ""  -> List("", "c", "b", "a", ""),
            Path.empty / "a"                  -> List("a"),
            Path.empty / "a" / "b"            -> List("b", "a"),
            Path.empty / "a" / "b" / "c" / "" -> List("", "c", "b", "a"),
          ),
        )

        checkAll(gen) { case (path, expected) =>
          assertTrue(expand(path).reverse == expected)
        }
      },
    ),
    suite("decode")(
      test("segments") {
        import Path.Flags

        // Internal representation of a path
        val paths = Gen.fromIterable(
          Seq(
            "/"       -> Path.root                   -> Path(Flags(Flag.LeadingSlash), Chunk.empty),
            "/a"      -> Path.root / a               -> Path(Flags(Flag.LeadingSlash), Chunk("a")),
            "/a/b"    -> Path.root / a / b           -> Path(Flags(Flag.LeadingSlash), Chunk("a", "b")),
            "/a/b/c"  -> Path.root / a / b / c       -> Path(Flags(Flag.LeadingSlash), Chunk("a", "b", "c")),
            "a/b/c"   -> Path.empty / a / b / c      -> Path(Flags.none, Chunk("a", "b", "c")),
            "a/b"     -> Path.empty / a / b          -> Path(Flags.none, Chunk("a", "b")),
            "a"       -> Path.empty / a              -> Path(Flags.none, Chunk("a")),
            ""        -> Path.empty                  -> Path(Flags.none, Chunk.empty),
            "a/"      -> Path.empty / a / ""         -> Path(Flags(Flag.TrailingSlash), Chunk("a")),
            "a/b/"    -> Path.empty / a / b / ""     -> Path(Flags(Flag.TrailingSlash), Chunk("a", "b")),
            "a/b/c/"  -> Path.empty / a / b / c / "" -> Path(Flags(Flag.TrailingSlash), Chunk("a", "b", "c")),
            "/a/b/c/" -> Path.root / a / b / c / ""  -> Path(
              Flags(Flag.LeadingSlash, Flag.TrailingSlash),
              Chunk("a", "b", "c"),
            ),
            "/a/b/"   -> Path.root / a / b / ""      -> Path(Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk("a", "b")),
            "/a/"     -> Path.root / a / ""          -> Path(Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk("a")),
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
        assertTrue(decoded == Path.root / a / b / c)
      },
      test("multiple trailing slashes") {
        val encoded = "a/b/c///"
        val decoded = Path.decode(encoded)
        assertTrue(decoded == (Path.empty / a / b / c).addTrailingSlash)
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
            Path.root                   -> Path.root.addLeadingSlash,
            Path.root                   -> Path.root.addLeadingSlash.addTrailingSlash,
            Path.empty.addTrailingSlash -> Path.empty.addTrailingSlash.addTrailingSlash.addTrailingSlash,
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
        assertTrue("a" /: Path.root == Path("a/"))
      },
      test("appending turns a root into a path with a leading slash") {
        assertTrue(Path.root / "a" == Path("/a"))
      },
      test("root is not empty, but empty is") {
        assertTrue(Path.root.isEmpty == false) &&
        assertTrue(Path.empty.isEmpty == true)
      },
    ),
    suite("startsWith")(
      test("isTrue") {
        val gen = Gen.fromIterable(
          Seq(
            Path.root                   -> Path.root,
            Path.root / "a"             -> Path.root / "a",
            Path.root / "a" / "b"       -> Path.root / "a" / "b",
            Path.root / "a" / "b" / "c" -> Path.root / "a",
            Path.root / "a" / "b" / "c" -> Path.root / "a" / "b" / "c",
            Path.root / "a" / "b" / "c" -> Path.root / "a" / "b" / "c",
            Path.root / "a" / "b" / "c" -> Path.root / "a" / "b" / "c",
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
            Path.root             -> Path.root / "a",
            Path.root / "a"       -> Path.root / "a" / "b",
            Path.root / "a"       -> Path.root / "b",
            Path.root / "a" / "b" -> Path.root / "a" / "b" / "c",
            Path.empty / "a"      -> Path.root / "a",
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
          (1, Path.root)                        -> Path.root,
          (1, Path.root / "a")                  -> Path.root,
          (1, Path.root / "a" / "b")            -> Path.root,
          (1, Path.root / "a" / "b" / "c")      -> Path.root,
          (2, Path.root / "a" / "b" / "c")      -> Path.root / "a",
          (3, Path.root / "a" / "b" / "c")      -> Path.root / "a" / "b",
          (4, Path.root / "a" / "b" / "c")      -> Path.root / "a" / "b" / "c",
          (1, Path.root)                        -> Path.root / "",
          (2, Path.root / "a" / "")             -> Path.root / "a",
          (3, Path.root / "a" / "b" / "")       -> Path.root / "a" / "b",
          (4, Path.root / "a" / "b" / "c" / "") -> Path.root / "a" / "b" / "c",
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
          (1, Path.root)                   -> Path.empty,
          (1, Path.root / "a")             -> Path.empty / "a",
          (1, Path.root / "a" / "b")       -> Path.empty / "a" / "b",
          (1, Path.root / "a" / "b" / "c") -> Path.empty / "a" / "b" / "c",
          (2, Path.root / "a" / "b" / "c") -> Path.empty / "b" / "c",
          (3, Path.root / "a" / "b" / "c") -> Path.empty / "c",
          (4, Path.root / "a" / "b" / "c") -> Path.empty,
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
          (1, Path.root)                   -> Path.empty,
          (1, Path.root / "a")             -> Path.root,
          (1, Path.root / "a" / "b")       -> Path.root / "a",
          (1, Path.root / "a" / "b" / "c") -> Path.root / "a" / "b",
          (2, Path.root / "a" / "b" / "c") -> Path.root / "a",
          (3, Path.root / "a" / "b" / "c") -> Path.root,
          (4, Path.root / "a" / "b" / "c") -> Path.empty,
          (1, Path.empty / "a" / "")       -> Path.empty / "a",
          (1, Path.root / "a" / "")        -> Path.root / "a",
          (2, Path.empty / "a" / "")       -> Path.empty,
          (2, Path.root / "a" / "")        -> Path.root,
          (3, Path.empty / "a" / "")       -> Path.empty,
          (3, Path.root / "a" / "")        -> Path.empty,
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

          assertTrue((path ++ Path.root).hasTrailingSlash) &&
          assertTrue((path ++ weirdRoot1).hasTrailingSlash) &&
          assertTrue((path ++ weirdRoot2).hasTrailingSlash) &&
          assertTrue((path ++ weirdRoot3).hasTrailingSlash)
        }
      },
      test("can add leading slash") {
        check(HttpGen.nonEmptyPath) { path =>
          val weirdRoot1 = Path(Path.Flags(Flag.LeadingSlash), Chunk.empty)
          val weirdRoot2 = Path(Path.Flags(Flag.LeadingSlash, Flag.TrailingSlash), Chunk.empty)
          val weirdRoot3 = Path(Path.Flags(Flag.TrailingSlash), Chunk.empty)

          assertTrue((Path.root ++ path).hasLeadingSlash) &&
          assertTrue((weirdRoot1 ++ path).hasLeadingSlash) &&
          assertTrue((weirdRoot2 ++ path).hasLeadingSlash) &&
          assertTrue((weirdRoot3 ++ path).hasLeadingSlash)
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
    suite("removeDotSegments")(
      test("only leading slash and dots") {
        val path     = Path.decode("/./../")
        val result   = path.removeDotSegments
        val expected = Path.root

        assertTrue(result == expected)
      },
      test("only leading dots") {
        val path     = Path.decode("./../")
        val result   = path.removeDotSegments
        val expected = Path.empty

        assertTrue(result == expected)
      },
      test("leading slash and dots") {
        val path     = Path.decode("/./../path")
        val result   = path.removeDotSegments
        val expected = Path.decode("/path")

        assertTrue(result == expected)
      },
      test("leading dots and path") {
        val path     = Path.decode("./../path")
        val result   = path.removeDotSegments
        val expected = Path.decode("path")

        assertTrue(result == expected)
      },
      test("double dot to top") {
        val path     = Path.decode("path/../subpath")
        val result   = path.removeDotSegments
        val expected = Path.decode("/subpath")

        assertTrue(result == expected)
      },
      test("trailing double dots") {
        val path     = Path.decode("path/ignored/..")
        val result   = path.removeDotSegments
        val expected = Path.decode("path/")

        assertTrue(result == expected)
      },
      test("path traversal") {
        val path     = Path.decode("/start/ignored/./../path/also/ignored/../../end/.")
        val result   = path.removeDotSegments
        val expected = Path.decode("/start/path/end/")

        assertTrue(result == expected)
      },
    ),
  )
}
