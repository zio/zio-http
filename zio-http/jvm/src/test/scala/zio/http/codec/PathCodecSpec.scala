/*
 * Copyright 2023 the ZIO HTTP contributors.
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

package zio.http.codec

import java.util.UUID

import scala.util.Try

import zio._
import zio.test._

import zio.http._
import zio.http.codec.PathCodec.Segment
import zio.http.codec._

object PathCodecSpec extends ZIOHttpSpec {
  final case class UserId(value: Int)
  final case class PostId(value: String)

  def spec =
    suite("PathCodecSpec")(
      suite("parsing")(
        test("empty") {
          val codec = PathCodec.path("")

          assertTrue(codec.segments.length == 1)
        },
        test("/users") {
          val codec = PathCodec.path("/users")

          assertTrue(codec.segments.length == 1)
        },
        test("/users/{user-id}/posts/{post-id}") {
          val codec =
            PathCodec.path("/users") / PathCodec.int("user-id") / PathCodec.literal("posts") / PathCodec
              .string(
                "post-id",
              )

          assertTrue(codec.segments.length == 4)
        },
        test("transformed") {
          val codec =
            PathCodec.path("/users") /
              PathCodec.int("user-id").transform(UserId.apply)(_.value) /
              PathCodec.literal("posts") /
              PathCodec
                .string("post-id")
                .transformOrFailLeft(s =>
                  Try(s.toInt).toEither.left.map(_ => "Not a number").map(n => PostId(n.toString)),
                )(_.value)
          assertTrue(codec.segments.length == 4)
        },
      ),
      suite("decoding")(
        test("empty") {
          val codec = PathCodec.empty

          assertTrue(codec.decode(Path.empty) == Right(())) &&
          assertTrue(codec.decode(Path.root) == Right(()))
        },
        test("trailing slashes") {
          val codec = PathCodec("/users") ++ PathCodec.trailing

          assertTrue(codec.decode(Path("/users")) == Right(Path.empty)) &&
          assertTrue(codec.decode(Path("/users/")) == Right(Path.root))
        },
        test("wildcard") {
          val codec = PathCodec.trailing

          assertTrue(codec.decode(Path.empty) == Right(Path.empty)) &&
          assertTrue(codec.decode(Path.root) == Right(Path.root)) &&
          assertTrue(codec.decode(Path("/users")) == Right(Path("/users")))
        },
        test("/users") {
          val codec = PathCodec.empty / PathCodec.literal("users")

          assertTrue(codec.decode(Path("/users")) == Right(())) &&
          assertTrue(codec.decode(Path("/users/")) == Right(()))
        },
        test("concat") {
          val codec1 = PathCodec.empty / PathCodec.literal("users") / PathCodec.int("user-id")
          val codec2 = PathCodec.empty / PathCodec.literal("posts") / PathCodec.string("post-id")

          val codec = codec1 ++ codec2

          assertTrue(codec.decode(Path("/users/1/posts/abc")) == Right((1, "abc")))
        },
        test("transformed") {
          val codec =
            PathCodec.path("/users") /
              PathCodec.int("user-id").transform(UserId.apply)(_.value) /
              PathCodec.literal("posts") /
              PathCodec
                .string("post-id")
                .transformOrFailLeft(s =>
                  Try(s.toInt).toEither.left.map(_ => "Not a number").map(n => PostId(n.toString)),
                )(_.value)
          assertTrue(
            codec.decode(Path("/users/1/posts/456")) == Right((UserId(1), PostId("456"))),
            codec.decode(Path("/users/1/posts/abc")) == Left("Not a number"),
          )
        },
      ),
      suite("UUID segment")(
        test("UUID segment decoding") {
          val codec = PathCodec.empty / "api" / PathCodec.uuid("entityId")
          val uuid  = UUID.randomUUID()
          val path  = Path(s"/api/$uuid")
          assertTrue(codec.decode(path) == Right(uuid))
        },
        test("UUID segment matches") {
          val codec = SegmentCodec.uuid("entityId")
          val uuid  = UUID.randomUUID().toString()
          val path  = Chunk("api", uuid)
          assertTrue(codec.matches(path, 1) == 1)
        },
      ),
      suite("decoding with sub-segment codecs")(
        test("int") {
          val codec = PathCodec.empty /
            string("foo") /
            "instances" /
            int("a") ~ "_" ~ int("b") /
            "bar" /
            int("baz")

          assertTrue(codec.decode(Path("/abc/instances/123_13/bar/42")) == Right(("abc", 123, 13, 42)))
        },
        test("uuid") {
          val codec = PathCodec.empty /
            string("foo") /
            "foo" /
            uuid("a") ~ "__" ~ int("b") /
            "bar" /
            int("baz")

          val id = UUID.randomUUID()
          val p  = s"/abc/foo/${id}__13/bar/42"
          assertTrue(codec.decode(Path(p)) == Right(("abc", id, 13, 42)))
        },
        test("uuid after string") {
          val codec = PathCodec.empty / "foo" / "bar" / string("baz") / "xyz" / uuid(
            "id",
          ) / "abc"
          val id    = UUID.randomUUID()
          val p     = s"/foo/bar/some_value/xyz/$id/abc"
          assertTrue(codec.decode(Path(p)) == Right(("some_value", id)))
        },
        test("string before literal") {
          val codec = PathCodec.empty /
            string("foo") /
            "foo" /
            string("a") ~ "__" ~ int("b") /
            "bar" /
            int("baz")
          assertTrue(codec.decode(Path("/abc/foo/cba__13/bar/42")) == Right(("abc", "cba", 13, 42)))
        },
        test("string before int") {
          val codec = PathCodec.empty /
            string("foo") /
            "foo" /
            string("a") ~ int("b") /
            "bar" /
            int("baz")
          assertTrue(codec.decode(Path("/abc/foo/cba13/bar/42")) == Right(("abc", "cba", 13, 42)))
        },
        test("string before long") {
          val codec = PathCodec.empty /
            string("foo") /
            "foo" /
            string("a") ~ long("b") /
            "bar" /
            int("baz")
          assertTrue(codec.decode(Path("/abc/foo/cba133333333333/bar/42")) == Right(("abc", "cba", 133333333333L, 42)))
        },
        test("trailing literal") {
          val codec = PathCodec.empty /
            string("foo") /
            "instances" /
            int("a") ~ "what" /
            "bar" /
            int("baz")

          assertTrue(codec.decode(Path("/abc/instances/123what/bar/42")) == Right(("abc", 123, 42)))
        },
      ),
      suite("representation")(
        test("empty") {
          val codec = PathCodec.empty

          assertTrue(codec.segments == Chunk(SegmentCodec.empty))
        },
        test("/users") {
          val codec = PathCodec.empty / PathCodec.literal("users")

          assertTrue(
            codec.segments ==
              Chunk(SegmentCodec.empty, SegmentCodec.literal("users")),
          )
        },
      ),
      suite("render")(
        test("empty") {
          val codec = PathCodec.empty

          assertTrue(codec.render == "")
        },
        test("/users") {
          val codec = PathCodec.empty / PathCodec.literal("users")

          assertTrue(codec.render == "/users")
        },
        test("/users/{user-id}/posts/{post-id}") {
          val codec =
            PathCodec.empty / PathCodec.literal("users") / PathCodec.int("user-id") / PathCodec.literal(
              "posts",
            ) / PathCodec.string("post-id")

          assertTrue(codec.render == "/users/{user-id}/posts/{post-id}")
        },
        test("/users/{first-name}_{last-name}") {
          val codec =
            PathCodec.empty / PathCodec.literal("users") /
              string("first-name") ~ "_" ~ string("last-name")

          assertTrue(codec.render == "/users/{first-name}_{last-name}")
        },
        test("transformed") {
          val codec =
            PathCodec.path("/users") /
              PathCodec.int("user-id").transform(UserId.apply)(_.value) /
              PathCodec.literal("posts") /
              PathCodec
                .string("post-id")
                .transformOrFailLeft(s =>
                  Try(s.toInt).toEither.left.map(_ => "Not a number").map(n => PostId(n.toString)),
                )(_.value)

          assertTrue(
            codec.render == "/users/{user-id}/posts/{post-id}",
          )
        },
      ),
    )
}
