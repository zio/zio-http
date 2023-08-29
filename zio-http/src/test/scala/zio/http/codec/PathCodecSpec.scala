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

          assertTrue(codec.segments.length == 2)
        },
        test("/users/{user-id}/posts/{post-id}") {
          val codec =
            PathCodec.path("/users") / SegmentCodec.int("user-id") / SegmentCodec.literal("posts") / SegmentCodec
              .string(
                "post-id",
              )

          assertTrue(codec.segments.length == 5)
        },
        test("transformed") {
          val codec =
            PathCodec.path("/users") /
              SegmentCodec.int("user-id").transform(UserId.apply)(_.value) /
              SegmentCodec.literal("posts") /
              SegmentCodec
                .string("post-id")
                .transformOrFailLeft(s =>
                  Try(s.toInt).toEither.left.map(_ => "Not a number").map(n => PostId(n.toString)),
                )(_.value)
          assertTrue(codec.segments.length == 5)
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
          val codec = PathCodec.empty / SegmentCodec.literal("users")

          assertTrue(codec.decode(Path("/users")) == Right(())) &&
          assertTrue(codec.decode(Path("/users/")) == Right(()))
        },
        test("concat") {
          val codec1 = PathCodec.empty / SegmentCodec.literal("users") / SegmentCodec.int("user-id")
          val codec2 = PathCodec.empty / SegmentCodec.literal("posts") / SegmentCodec.string("post-id")

          val codec = codec1 ++ codec2

          assertTrue(codec.decode(Path("/users/1/posts/abc")) == Right((1, "abc")))
        },
        test("transformed") {
          val codec =
            PathCodec.path("/users") /
              SegmentCodec.int("user-id").transform(UserId.apply)(_.value) /
              SegmentCodec.literal("posts") /
              SegmentCodec
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
      suite("representation")(
        test("empty") {
          val codec = PathCodec.empty

          assertTrue(codec.segments == Chunk(SegmentCodec.empty))
        },
        test("/users") {
          val codec = PathCodec.empty / SegmentCodec.literal("users")

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
          val codec = PathCodec.empty / SegmentCodec.literal("users")

          assertTrue(codec.render == "/users")
        },
        test("/users/{user-id}/posts/{post-id}") {
          val codec =
            PathCodec.empty / SegmentCodec.literal("users") / SegmentCodec.int("user-id") / SegmentCodec.literal(
              "posts",
            ) / SegmentCodec.string("post-id")

          assertTrue(codec.render == "/users/{user-id}/posts/{post-id}")
        },
        test("transformed") {
          val codec =
            PathCodec.path("/users") /
              SegmentCodec.int("user-id").transform(UserId.apply)(_.value) /
              SegmentCodec.literal("posts") /
              SegmentCodec
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
