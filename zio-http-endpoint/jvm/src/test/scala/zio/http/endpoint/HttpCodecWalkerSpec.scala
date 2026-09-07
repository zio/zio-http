/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

import zio.blocks.combinators.{Eithers, Tuples}
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{Alternator, AuthType, CodecKind, Endpoint, HttpCodec, PathCodec, RoutePattern}
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.schema.Schema
import zio.http.{Method, Path}
import zio.test._

/**
 * Spec for [[EndpointCodecWalker]]: decomposing an endpoint `Input` value into
 * path / query / header / body slots by walking the `HttpCodec` ADT
 * explicitly.
 *
 * Cross-version note: this file lives in the version-shared test sources and
 * must compile on BOTH Scala 2.13 and Scala 3. It therefore uses only shared
 * syntax (no `derives`, no union types, no significant indentation) and only
 * primitive schemas (no `Schema.derived`, which differs per toolchain).
 */
object HttpCodecWalkerSpec extends ZIOSpecDefault {

  private def eitherEithers[L, R]: Eithers.Eithers.WithOut[L, R, Either[L, R]] =
    new Eithers.Eithers[L, R] {
      type Out = Either[L, R]
      def combine(either: Either[L, R]): Either[L, R] = either
      def separate(out: Either[L, R]): Either[L, R]   = out
    }

  private val errorCodec: HttpCodec[CodecKind.Response, String] =
    HttpCodec.responseBody(Schema[String])

  private val outputCodec: HttpCodec[CodecKind.Response, String] =
    HttpCodec.responseBody(Schema[String])

  private val userRoute: RoutePattern[Int] =
    Method.GET / "users" / PathCodec.int("id")

  /**
   * Tuple-append combiners, passed EXPLICITLY to `++`.
   *
   * The pinned zio-blocks snapshot predates `Tuples.tupleValue`/`tupleTuple`
   * on Scala 3, so a 3-element `++` chain would infer different shapes per
   * toolchain (Scala 3 `fallback` nests, the 2.13 macro appends flat). These
   * hand-rolled instances pin the flat shape on BOTH toolchains; explicit
   * application skips implicit search entirely, so there is no ambiguity with
   * the companion-provided instances.
   */
  private val appendBodyString: Tuples.Tuples.WithOut[(Boolean, String), String, (Boolean, String, String)] =
    new Tuples.Tuples[(Boolean, String), String] {
      type Out = (Boolean, String, String)
      def combine(left: (Boolean, String), right: String): Out = (left._1, left._2, right)
      def separate(out: Out): ((Boolean, String), String)     = ((out._1, out._2), out._3)
    }

  private val appendThirdInt: Tuples.Tuples.WithOut[(Int, Int), Int, (Int, Int, Int)] =
    new Tuples.Tuples[(Int, Int), Int] {
      type Out = (Int, Int, Int)
      def combine(left: (Int, Int), right: Int): Out = (left._1, left._2, right)
      def separate(out: Out): ((Int, Int), Int)     = ((out._1, out._2), out._3)
    }

  private val appendFourthString: Tuples.Tuples.WithOut[(Int, Int, Int), String, (Int, Int, Int, String)] =
    new Tuples.Tuples[(Int, Int, Int), String] {
      type Out = (Int, Int, Int, String)
      def combine(left: (Int, Int, Int), right: String): Out = (left._1, left._2, left._3, right)
      def separate(out: Out): ((Int, Int, Int), String)     = ((out._1, out._2, out._3), out._4)
    }

  private val queryHeaderCodec: HttpCodec[CodecKind.Request, (Boolean, String)] =
    HttpCodec.query[Boolean]("active", Schema[Boolean]) ++
      HttpCodec.requestHeader[String]("X-Trace", Schema[String])

  private val usersInputCodec: HttpCodec[CodecKind.Request, (Boolean, String, String)] =
    queryHeaderCodec.++[String, (Boolean, String, String)](
      HttpCodec.requestBody(Schema[String]),
    )(appendBodyString)

  private val usersEndpoint: Endpoint[Int, (Boolean, String, String), String, String, AuthType.None.type] =
    Endpoint(userRoute, usersInputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)

  def spec = suite("HttpCodecWalker")(
    test("GET /users/{id}?active={active} + X-Trace + JSON body decomposes into all 4 slots") {
      val result = EndpointCodecWalker.decompose(usersEndpoint, 42, (true, "trace-1", "payload"))
      assertTrue(
        result.map(_.pathParams) == Right(42),
        result.map(_.path) == Right(Path.root / "users" / "42"),
        result.map(_.queryParams) == Right(Map("active" -> "true")),
        result.map(_.headers) == Right(Map("X-Trace" -> "trace-1")),
        result.map(_.body.map(b => Schema[String].jsonCodec.decode(b.toArray))) == Right(
          Some(Right("payload")),
        ),
      )
    },
    test("Fallback picks the matching alternative on each side") {
      val eithers: Eithers.Eithers.WithOut[Int, String, Either[Int, String]] =
        eitherEithers[Int, String]
      val codec: HttpCodec[CodecKind.Request, Either[Int, String]] =
        HttpCodec.Fallback(
          HttpCodec.query[Int]("a", Schema[Int]),
          HttpCodec.query[String]("b", Schema[String]),
          Alternator.fromEithers(eithers),
        )
      val route: RoutePattern[Unit] = RoutePattern(Method.GET, Path.root / "fb")
      val leftResult                = EndpointCodecWalker.decompose(route, codec, (), Left(1))
      val rightResult               = EndpointCodecWalker.decompose(route, codec, (), Right("x"))
      assertTrue(
        leftResult.map(_.queryParams) == Right(Map("a" -> "1")),
        rightResult.map(_.queryParams) == Right(Map("b" -> "x")),
      )
    },
    test("3-level nested Combine splits every fragment") {
      // Left-nested spine Combine(Combine(Combine(n1, n2), n3), body) with
      // the flat tuple shape pinned explicitly via the append combiners above
      // so both toolchains agree.
      val pair12: HttpCodec[CodecKind.Request, (Int, Int)] =
        HttpCodec.query[Int]("n1", Schema[Int]) ++ HttpCodec.query[Int]("n2", Schema[Int])
      val triple123: HttpCodec[CodecKind.Request, (Int, Int, Int)] =
        pair12.++[Int, (Int, Int, Int)](HttpCodec.query[Int]("n3", Schema[Int]))(appendThirdInt)
      val codec: HttpCodec[CodecKind.Request, (Int, Int, Int, String)] =
        triple123.++[String, (Int, Int, Int, String)](HttpCodec.requestBody(Schema[String]))(
          appendFourthString,
        )
      val route: RoutePattern[Unit] = RoutePattern(Method.POST, Path.root / "nested")
      val result                    = EndpointCodecWalker.decompose(route, codec, (), (1, 2, 3, "b"))
      assertTrue(
        result.map(_.queryParams) == Right(Map("n1" -> "1", "n2" -> "2", "n3" -> "3")),
        result.map(_.body.map(b => Schema[String].jsonCodec.decode(b.toArray))) == Right(Some(Right("b"))),
      )
    },
    test("Empty codec decomposes to empty slots") {
      val route: RoutePattern[Unit] = RoutePattern(Method.GET, Path.root / "empty")
      val result                    = EndpointCodecWalker.decompose(route, HttpCodec.empty[CodecKind.Request], (), ())
      assertTrue(
        result.map(_.queryParams) == Right(Map.empty[String, String]),
        result.map(_.headers) == Right(Map.empty[String, String]),
        result.map(_.body) == Right(None),
      )
    },
    test("Fallback against Empty omits the absent optional query param") {
      val eithers: Eithers.Eithers.WithOut[Boolean, Unit, Either[Boolean, Unit]] =
        eitherEithers[Boolean, Unit]
      val codec: HttpCodec[CodecKind.Request, Either[Boolean, Unit]] =
        HttpCodec.Fallback(
          HttpCodec.query[Boolean]("active", Schema[Boolean]),
          HttpCodec.Empty,
          Alternator.fromEithers(eithers),
        )
      val route: RoutePattern[Unit] = RoutePattern(Method.GET, Path.root / "opt")
      val present                   = EndpointCodecWalker.decompose(route, codec, (), Left(true))
      val absent                    = EndpointCodecWalker.decompose(route, codec, (), Right(()))
      assertTrue(
        present.map(_.queryParams) == Right(Map("active" -> "true")),
        absent.map(_.queryParams) == Right(Map.empty[String, String]),
      )
    },
    test("combining two bodies fails with a clear error instead of dropping one") {
      val codec =
        HttpCodec.requestBody(Schema[String]) ++ HttpCodec.requestBody(Schema[Int])
      val route: RoutePattern[Unit] = RoutePattern(Method.POST, Path.root / "twobody")
      val result                    = EndpointCodecWalker.decompose(route, codec, (), ("a", 1))
      assertTrue(
        result.isLeft,
        result.left.getOrElse("").contains("body"),
      )
    },
    test("a broken alternator fails with a clear dispatch error") {
      val boom = new Alternator[Int, String] {
        type Out = Either[Int, String]
        def combine(either: Either[Int, String]): Either[Int, String] = either
        def separate(out: Either[Int, String]): Either[Int, String]   =
          throw new RuntimeException("boom-no-dispatch")
      }
      val codec: HttpCodec[CodecKind.Request, Either[Int, String]] =
        HttpCodec.Fallback(
          HttpCodec.query[Int]("a", Schema[Int]),
          HttpCodec.query[String]("b", Schema[String]),
          boom,
        )
      val route: RoutePattern[Unit] = RoutePattern(Method.GET, Path.root / "boom")
      val result                    = EndpointCodecWalker.decompose(route, codec, (), Left(1))
      assertTrue(
        result.isLeft,
        result.left.getOrElse("").contains("boom-no-dispatch"),
      )
    },
  )
}
