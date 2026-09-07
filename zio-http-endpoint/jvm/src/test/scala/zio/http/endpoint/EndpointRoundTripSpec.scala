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

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.{Eithers, Tuples}
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{Alternator, AuthType, CodecKind, Endpoint, HttpCodec, PathCodec, RoutePattern}
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.Schema
import zio.http.{Body, ContentType, Method, Path}
import zio.test._

/**
 * Todo 12: endpoint round-trip through the walker + fixed `buildRequest`.
 *
 * Every test walks the SAME path the production bridge walks:
 * `EndpointCodecWalker.decompose` -> `EndpointBridge.buildRequestPublic` ->
 * server-side `EndpointCodec.decodeRequest`, asserting value equality at the
 * end. The non-root `/users/{id}` route covers the exact shape from
 * `EndpointCallRoundtripSpec`'s URL.root bug note: the rendered request must
 * carry the real path, never `URL.root`.
 *
 * Response headers go through `EndpointCodec.encodeResponse` /
 * `decodeResponse`: a `Header` node in the output codec must land on the real
 * [[zio.http.Response]] headers, not be dropped.
 *
 * Cross-version note: shared test sources, so shared syntax only (no `derives`,
 * no union types), only primitive schemas, and combiner/alternator instances
 * passed explicitly.
 */
object EndpointRoundTripSpec extends ZIOSpecDefault {

  private def eitherEithers[L, R]: Eithers.Eithers.WithOut[L, R, Either[L, R]] =
    new Eithers.Eithers[L, R] {
      type Out = Either[L, R]
      def combine(either: Either[L, R]): Either[L, R] = either
      def separate(out: Either[L, R]): Either[L, R]   = out
    }

  private val appendBodyString: Tuples.Tuples.WithOut[(Boolean, String), String, (Boolean, String, String)] =
    new Tuples.Tuples[(Boolean, String), String] {
      type Out = (Boolean, String, String)
      def combine(left: (Boolean, String), right: String): Out = (left._1, left._2, right)
      def separate(out: Out): ((Boolean, String), String)      = ((out._1, out._2), out._3)
    }

  private val appendThirdInt: Tuples.Tuples.WithOut[(Int, Int), Int, (Int, Int, Int)] =
    new Tuples.Tuples[(Int, Int), Int] {
      type Out = (Int, Int, Int)
      def combine(left: (Int, Int), right: Int): Out = (left._1, left._2, right)
      def separate(out: Out): ((Int, Int), Int)      = ((out._1, out._2), out._3)
    }

  private val appendFourthString: Tuples.Tuples.WithOut[(Int, Int, Int), String, (Int, Int, Int, String)] =
    new Tuples.Tuples[(Int, Int, Int), String] {
      type Out = (Int, Int, Int, String)
      def combine(left: (Int, Int, Int), right: String): Out = (left._1, left._2, left._3, right)
      def separate(out: Out): ((Int, Int, Int), String)      = ((out._1, out._2, out._3), out._4)
    }

  private val errorCodec: HttpCodec[CodecKind.Response, String] =
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
    Endpoint(usersRoute, usersInputCodec, errorCodec, errorCodec, AuthType.None, Doc.empty)

  private val optRoute: RoutePattern[Unit] =
    RoutePattern(Method.GET, Path.root / "opt")

  private def optEndpoint: Endpoint[Unit, Either[Boolean, Unit], String, String, AuthType.None.type] = {
    val eithers: Eithers.Eithers.WithOut[Boolean, Unit, Either[Boolean, Unit]] =
      eitherEithers[Boolean, Unit]
    val codec: HttpCodec[CodecKind.Request, Either[Boolean, Unit]]             =
      HttpCodec.Fallback(
        HttpCodec.query[Boolean]("active", Schema[Boolean]),
        HttpCodec.Empty,
        Alternator.fromEithers(eithers),
      )
    Endpoint(optRoute, codec, errorCodec, errorCodec, AuthType.None, Doc.empty)
  }

  private val fbRoute: RoutePattern[Unit] =
    RoutePattern(Method.GET, Path.root / "fb")

  private def fbEndpoint: Endpoint[Unit, Either[Int, String], String, String, AuthType.None.type] = {
    val eithers: Eithers.Eithers.WithOut[Int, String, Either[Int, String]] =
      eitherEithers[Int, String]
    val codec: HttpCodec[CodecKind.Request, Either[Int, String]]           =
      HttpCodec.Fallback(
        HttpCodec.query[Int]("a", Schema[Int]),
        HttpCodec.query[String]("b", Schema[String]),
        Alternator.fromEithers(eithers),
      )
    Endpoint(fbRoute, codec, errorCodec, errorCodec, AuthType.None, Doc.empty)
  }

  private val nestedRoute: RoutePattern[Unit] =
    RoutePattern(Method.POST, Path.root / "nested")

  private def nestedEndpoint: Endpoint[Unit, (Int, Int, Int, String), String, String, AuthType.None.type] = {
    val pair12: HttpCodec[CodecKind.Request, (Int, Int)]             =
      HttpCodec.query[Int]("n1", Schema[Int]) ++ HttpCodec.query[Int]("n2", Schema[Int])
    val triple123: HttpCodec[CodecKind.Request, (Int, Int, Int)]     =
      pair12.++[Int, (Int, Int, Int)](HttpCodec.query[Int]("n3", Schema[Int]))(appendThirdInt)
    val codec: HttpCodec[CodecKind.Request, (Int, Int, Int, String)] =
      triple123.++[String, (Int, Int, Int, String)](HttpCodec.requestBody(Schema[String]))(
        appendFourthString,
      )
    Endpoint(nestedRoute, codec, errorCodec, errorCodec, AuthType.None, Doc.empty)
  }

  private val searchRoute: RoutePattern[Unit] =
    RoutePattern(Method.GET, Path.root / "search")

  private def searchEndpoint: Endpoint[Unit, String, String, String, AuthType.None.type] = {
    val codec: HttpCodec[CodecKind.Request, String] =
      HttpCodec.query[String]("q", Schema[String])
    Endpoint(searchRoute, codec, errorCodec, errorCodec, AuthType.None, Doc.empty)
  }

  private val headedOutputCodec: HttpCodec[CodecKind.Response, (String, String)] =
    HttpCodec.responseHeader[String]("X-Req-Id", Schema[String]) ++
      HttpCodec.responseBody[String](Schema[String])

  private val csvBodyCodec: HttpCodec[CodecKind.Request, String] =
    HttpCodec.requestBody(Schema[String], Chunk(MediaType("text", "csv")))

  private val csvRoute: RoutePattern[Unit] =
    RoutePattern(Method.POST, Path.root / "csv")

  private def csvEndpoint: Endpoint[Unit, String, String, String, AuthType.None.type] =
    Endpoint(csvRoute, csvBodyCodec, errorCodec, errorCodec, AuthType.None, Doc.empty)

  private val jsonContentType: ContentType = ContentType.`application/json`

  def spec = suite("EndpointRoundTrip")(
    test("path + query + header + body round-trips through buildRequest and server-side decode") {
      val input   = (true, "trace-1", "payload")
      val request = EndpointBridge.buildRequestPublic(usersEndpoint, 42, input)
      val decoded = EndpointCodec.decodeRequest(usersEndpoint.input, request)
      assertTrue(
        request.url.path == Path.root / "users" / "42",
        request.url.path != Path.root,
        request.url.queryParams.getFirst("active") == Some("true"),
        request.headers.rawGet("X-Trace") == Some("trace-1"),
        decoded == Right(input),
        decoded != Right((false, "", "")),
      )
    },
    test("optional-absent vs present query round-trips both directions") {
      val present = EndpointBridge.buildRequestPublic(optEndpoint, (), Left(true))
      val absent  = EndpointBridge.buildRequestPublic(optEndpoint, (), Right(()))
      assertTrue(
        present.url.queryParams.getFirst("active") == Some("true"),
        absent.url.queryParams.getFirst("active").isEmpty,
        EndpointCodec.decodeRequest(optEndpoint.input, present) == Right(Left(true)),
        EndpointCodec.decodeRequest(optEndpoint.input, absent) == Right(Right(())),
      )
    },
    test("Fallback branch A vs B round-trips both directions") {
      val left  = EndpointBridge.buildRequestPublic(fbEndpoint, (), Left(1))
      val right = EndpointBridge.buildRequestPublic(fbEndpoint, (), Right("x"))
      assertTrue(
        left.url.queryParams.getFirst("a") == Some("1"),
        right.url.queryParams.getFirst("b") == Some("x"),
        EndpointCodec.decodeRequest(fbEndpoint.input, left) == Right(Left(1)),
        EndpointCodec.decodeRequest(fbEndpoint.input, right) == Right(Right("x")),
      )
    },
    test("nested Combine round-trips every fragment") {
      val input   = (1, 2, 3, "b")
      val request = EndpointBridge.buildRequestPublic(nestedEndpoint, (), input)
      assertTrue(
        EndpointCodec.decodeRequest(nestedEndpoint.input, request) == Right(input),
        EndpointCodec.decodeRequest(nestedEndpoint.input, request) != Right((0, 0, 0, "")),
      )
    },
    test("special chars in query and path survive the round-trip off the root path") {
      val request = EndpointBridge.buildRequestPublic(searchEndpoint, (), "hello world")
      val pathEp: Endpoint[String, Unit, String, String, AuthType.None.type] = {
        val route: RoutePattern[String] = Method.GET / "search" / PathCodec.string("q")
        Endpoint(route, HttpCodec.empty[CodecKind.Request], errorCodec, errorCodec, AuthType.None, Doc.empty)
      }
      val pathRequest = EndpointBridge.buildRequestPublic(pathEp, "a b", ())
      assertTrue(
        EndpointCodec.decodeRequest(searchEndpoint.input, request) == Right("hello world"),
        pathRequest.url.path != Path.root,
        pathRequest.url.encode.contains("/search/"),
      )
    },
    test("endpoint Output headers encode onto the real Response headers, not dropped") {
      val response = EndpointCodec.encodeResponse(headedOutputCodec, ("req-1", "ok"))
      assertTrue(
        response.headers.rawGet("X-Req-Id") == Some("req-1"),
        Schema[String].jsonCodec.decode(response.body.toArray) == Right("ok"),
        EndpointCodec.decodeResponse(headedOutputCodec, response) == Right(("req-1", "ok")),
      )
    },
    test("wrong-mediaType body falls back to a JSON attempt with a clear error, not silent empty") {
      val garbage  = Body.fromArray("plain-text-bytes".getBytes("UTF-8"), jsonContentType)
      val bad      = EndpointBridge.buildRequestPublic(csvEndpoint, (), "ignored").copy(body = garbage)
      val result   = EndpointCodec.decodeRequest(csvEndpoint.input, bad)
      val goodBody = Body.fromArray(Schema[String].jsonCodec.encode("hi"), jsonContentType)
      val good     = EndpointBridge.buildRequestPublic(csvEndpoint, (), "ignored").copy(body = goodBody)
      assertTrue(
        result.isLeft,
        result.left.getOrElse("").contains("JSON"),
        result.left.getOrElse("").contains("text/csv"),
        EndpointCodec.decodeRequest(csvEndpoint.input, good) == Right("hi"),
      )
    },
  )
}
