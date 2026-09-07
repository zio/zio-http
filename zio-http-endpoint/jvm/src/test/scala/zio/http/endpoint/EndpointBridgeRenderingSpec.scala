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
 * Spec for `EndpointBridge.buildRequest` (Todo 11): the client side of `.call`
 * must render a FULL HTTP request from `EndpointCodecWalker.decompose` output —
 * method from the endpoint, path from `RoutePattern.format` (never `URL.root`;
 * see the `EndpointCallRoundtripSpec` note for the bug this fixes), query string
 * from the decomposed query params, headers (including `Content-Type` from the
 * body media type), and JSON body bytes from the codec walk.
 *
 * The fluent `Endpoint.get("/users/{id}").query(...).header(...).in[User]`
 * shape from the plan is expressed here with the real constructor API
 * (`RoutePattern` + `HttpCodec`); the rendered wire request is identical.
 *
 * Cross-version note: this file lives in the version-shared test sources and
 * must compile on BOTH Scala 2.13 and Scala 3. It therefore uses only shared
 * syntax (no `derives`, no union types, no significant indentation), only
 * primitive schemas, and passes combiner/alternator instances explicitly (never
 * relying on implicit search for tuples/eithers in shared sources).
 */
object EndpointBridgeRenderingSpec extends ZIOSpecDefault {

  private def eitherEithers[L, R]: Eithers.Eithers.WithOut[L, R, Either[L, R]] =
    new Eithers.Eithers[L, R] {
      type Out = Either[L, R]
      def combine(either: Either[L, R]): Either[L, R] = either
      def separate(out: Either[L, R]): Either[L, R]   = out
    }

  /**
   * Tuple-append combiner, passed EXPLICITLY to `++` (same rationale as
   * `HttpCodecWalkerSpec`: the pinned zio-blocks snapshot infers different
   * shapes per toolchain for multi-element `++` chains, so the flat shape is
   * pinned by hand and applied explicitly).
   */
  private val appendBodyString: Tuples.Tuples.WithOut[(Boolean, String), String, (Boolean, String, String)] =
    new Tuples.Tuples[(Boolean, String), String] {
      type Out = (Boolean, String, String)
      def combine(left: (Boolean, String), right: String): Out = (left._1, left._2, right)
      def separate(out: Out): ((Boolean, String), String)     = ((out._1, out._2), out._3)
    }

  private val errorCodec: HttpCodec[CodecKind.Response, String] =
    HttpCodec.responseBody(Schema[String])

  private val outputCodec: HttpCodec[CodecKind.Response, String] =
    HttpCodec.responseBody(Schema[String])

  private val usersRoute: RoutePattern[Int] =
    Method.GET / "users" / PathCodec.int("id")

  private val queryHeaderCodec: HttpCodec[CodecKind.Request, (Boolean, String)] =
    HttpCodec.query[Boolean]("active", Schema[Boolean]) ++
      HttpCodec.requestHeader[String]("X-Trace", Schema[String])

  private val usersInputCodec: HttpCodec[CodecKind.Request, (Boolean, String, String)] =
    queryHeaderCodec.++[String, (Boolean, String, String)](
      HttpCodec.requestBody(Schema[String]),
    )(appendBodyString)

  private val usersEndpoint: Endpoint[Int, (Boolean, String, String), String, String, AuthType.None.type] =
    Endpoint(usersRoute, usersInputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)

  private val emptyRoute: RoutePattern[Unit] =
    RoutePattern(Method.GET, Path.root / "empty")

  private val emptyEndpoint: Endpoint[Unit, Unit, String, String, AuthType.None.type] =
    Endpoint(emptyRoute, HttpCodec.empty[CodecKind.Request], errorCodec, outputCodec, AuthType.None, Doc.empty)

  def spec = suite("EndpointBridgeRendering")(
    test("GET /users/{id}?active={active} + X-Trace + JSON body renders the full request") {
      val request = EndpointBridge.buildRequestPublic(usersEndpoint, 42, (true, "trace-1", "payload"))
      assertTrue(
        request.method == Method.GET,
        request.url.path == Path.root / "users" / "42",
        request.url.path != Path.root,
        request.url.queryParams.getFirst("active") == Some("true"),
        request.headers.rawGet("X-Trace") == Some("trace-1"),
        request.headers.rawGet("Content-Type").exists(_.contains("application/json")),
        request.body.contentType.mediaType.fullType == "application/json",
        Schema[String].jsonCodec.decode(request.body.toArray) == Right("payload"),
      )
    },
    test("a missing required param fails with IllegalArgumentException naming the param") {
      val boom = new Alternator[String, Unit] {
        type Out = Either[String, Unit]
        def combine(either: Either[String, Unit]): Either[String, Unit] = either
        def separate(out: Either[String, Unit]): Either[String, Unit]   =
          throw new RuntimeException("missing required param 'session'")
      }
      val codec: HttpCodec[CodecKind.Request, Either[String, Unit]] =
        HttpCodec.Fallback(
          HttpCodec.requestHeader[String]("X-Session", Schema[String]),
          HttpCodec.Empty,
          boom,
        )
      val route: RoutePattern[Unit] = RoutePattern(Method.GET, Path.root / "guarded")
      val guarded: Endpoint[Unit, Either[String, Unit], String, String, AuthType.None.type] =
        Endpoint(route, codec, errorCodec, outputCodec, AuthType.None, Doc.empty)
      val result = try {
        EndpointBridge.buildRequestPublic(guarded, (), Left("s-1"))
        None
      } catch {
        case e: IllegalArgumentException => Some(e.getMessage)
      }
      assertTrue(
        result.isDefined,
        result.getOrElse("").contains("session"),
      )
    },
    test("an absent optional query param is omitted, a present one is rendered") {
      val eithers: Eithers.Eithers.WithOut[Boolean, Unit, Either[Boolean, Unit]] =
        eitherEithers[Boolean, Unit]
      val codec: HttpCodec[CodecKind.Request, Either[Boolean, Unit]] =
        HttpCodec.Fallback(
          HttpCodec.query[Boolean]("active", Schema[Boolean]),
          HttpCodec.Empty,
          Alternator.fromEithers(eithers),
        )
      val route: RoutePattern[Unit] = RoutePattern(Method.GET, Path.root / "opt")
      val opt: Endpoint[Unit, Either[Boolean, Unit], String, String, AuthType.None.type] =
        Endpoint(route, codec, errorCodec, outputCodec, AuthType.None, Doc.empty)
      val present = EndpointBridge.buildRequestPublic(opt, (), Left(true))
      val absent  = EndpointBridge.buildRequestPublic(opt, (), Right(()))
      assertTrue(
        present.url.queryParams.getFirst("active") == Some("true"),
        absent.url.queryParams.getFirst("active").isEmpty,
      )
    },
    test("an empty body codec renders Body.empty with no Content-Type header") {
      val request = EndpointBridge.buildRequestPublic(emptyEndpoint, (), ())
      assertTrue(
        request.method == Method.GET,
        request.url.path == Path.root / "empty",
        request.body.isEmpty,
        request.headers.rawGet("Content-Type").isEmpty,
      )
    },
    test("a special-char path param renders through RoutePattern.format, never as root") {
      val route: RoutePattern[String] = Method.GET / "search" / PathCodec.string("q")
      val ep: Endpoint[String, Unit, String, String, AuthType.None.type] =
        Endpoint(route, HttpCodec.empty[CodecKind.Request], errorCodec, outputCodec, AuthType.None, Doc.empty)
      val request = EndpointBridge.buildRequestPublic(ep, "a b", ())
      assertTrue(
        request.url.path != Path.root,
        request.url.encode.contains("/search/"),
      )
    },
  )
}
