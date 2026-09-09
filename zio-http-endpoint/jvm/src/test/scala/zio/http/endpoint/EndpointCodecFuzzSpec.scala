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

import zio.blocks.combinators.Tuples
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, PathCodec, RoutePattern}
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.schema.Schema
import zio.http.{Method, Path, QueryParams}
import zio.test._

/**
 * MAJOR-1 fuzz: generated breadth over the walker + `buildRequest` +
 * `decodeRequest` round trip.
 *
 * Every generated case walks the SAME path the production bridge walks:
 * `EndpointCodecWalker.decompose` -> `EndpointBridge.buildRequestPublic` ->
 * server-side `EndpointCodec.decodeRequest`, asserting value equality at the
 * end. The generator is hand-rolled (no new test deps): varying path params
 * (special chars, unicode, empty), query values (special chars, empty),
 * auth-shaped `Authorization: Bearer ...` headers, and JSON bodies of varying
 * shapes.
 *
 * The reviewer's named risks are called out explicitly:
 *
 *   - DUPLICATE headers: `Authorization` must appear EXACTLY once with the
 *     exact Bearer value — the walker emits one map entry per `Header` node and
 *     `buildRequest` adds each entry once.
 *   - DROPPED headers: every decomposed header must land on the built request.
 *   - Query-encoding mismatch: query values must survive render -> parse
 *     (`QueryParams.encode` -> `QueryParams.fromEncoded`) byte-stable.
 *
 * Cross-version note: this file lives in the version-shared test sources and
 * must compile on BOTH Scala 2.13 and Scala 3. It therefore uses only shared
 * syntax (no `derives`, no union types, no significant indentation), only
 * primitive schemas, and passes combiner instances explicitly.
 */
object EndpointCodecFuzzSpec extends ZIOSpecDefault {

  private final case class FuzzCase(
    slug: String,
    q: String,
    n: Int,
    auth: String,
    trace: String,
    body: String,
  )

  private val slugs: List[String] =
    List("plain", "a b", "unicodé-日本語", "a+b", "100%", "x.y-z_~", "a:b@c", "")

  private val queries: List[String] =
    List(
      "hello",
      "hello world",
      "a&b=c?d",
      "ünïcodé-日本",
      "",
      "a+b",
      "x/y?z#frag",
      "100% sure",
      "tab\there",
      "quote\"q\"",
      "back\\slash",
      "semi;colon",
    )

  private val numbers: List[Int] =
    List(0, 1, -42, 2147483647)

  private val auths: List[String] =
    List(
      "Bearer abc123",
      "Bearer token with spaces/and+slashes=equals",
      "Basic dXNlcjpwYXNz",
      "Bearer ünïcodé-🎉",
    )

  private val traces: List[String] =
    List("t-1", "trace with spaces", "")

  private val bodies: List[String] =
    List("", "payload", "hello \"quoted\" world", "line1\nline2", "ünïcodé 🎉", "{\"k\": [1,2]}")

  /**
   * Deterministic 64-case corpus: the (slug, q) grid alone yields 64 distinct
   * pairs, while the remaining slots cycle with coprime strides for breadth.
   */
  private val cases: List[FuzzCase] =
    (0 until 64).map { i =>
      FuzzCase(
        slug = slugs(i % slugs.size),
        q = queries((i / 8) % queries.size),
        n = numbers((i / 3) % numbers.size),
        auth = auths((i / 7) % auths.size),
        trace = traces((i / 11) % traces.size),
        body = bodies((i / 13) % bodies.size),
      )
    }.toList

  private type Quint = (String, Int, String, String, String)

  private val appendThirdS: Tuples.Tuples.WithOut[(String, Int), String, (String, Int, String)] =
    new Tuples.Tuples[(String, Int), String] {
      type Out = (String, Int, String)
      def combine(left: (String, Int), right: String): Out = (left._1, left._2, right)
      def separate(out: Out): ((String, Int), String)      = ((out._1, out._2), out._3)
    }

  private val appendFourthS: Tuples.Tuples.WithOut[(String, Int, String), String, (String, Int, String, String)] =
    new Tuples.Tuples[(String, Int, String), String] {
      type Out = (String, Int, String, String)
      def combine(left: (String, Int, String), right: String): Out = (left._1, left._2, left._3, right)
      def separate(out: Out): ((String, Int, String), String)      = ((out._1, out._2, out._3), out._4)
    }

  private val appendFifthS
    : Tuples.Tuples.WithOut[(String, Int, String, String), String, (String, Int, String, String, String)] =
    new Tuples.Tuples[(String, Int, String, String), String] {
      type Out = (String, Int, String, String, String)
      def combine(left: (String, Int, String, String), right: String): Out =
        (left._1, left._2, left._3, left._4, right)
      def separate(out: Out): ((String, Int, String, String), String)      =
        ((out._1, out._2, out._3, out._4), out._5)
    }

  private val errorCodec: HttpCodec[CodecKind.Response, String] =
    HttpCodec.responseBody(Schema[String])

  private val fuzzRoute: RoutePattern[String] =
    Method.GET / "fuzz" / PathCodec.string("slug")

  private val qnCodec: HttpCodec[CodecKind.Request, (String, Int)] =
    HttpCodec.query[String]("q", Schema[String]) ++ HttpCodec.query[Int]("n", Schema[Int])

  private val qnAuthCodec: HttpCodec[CodecKind.Request, (String, Int, String)] =
    qnCodec.++[String, (String, Int, String)](
      HttpCodec.requestHeader[String]("Authorization", Schema[String]),
    )(appendThirdS)

  private val qnAuthTraceCodec: HttpCodec[CodecKind.Request, (String, Int, String, String)] =
    qnAuthCodec.++[String, (String, Int, String, String)](
      HttpCodec.requestHeader[String]("X-Trace", Schema[String]),
    )(appendFourthS)

  private val fuzzInputCodec: HttpCodec[CodecKind.Request, Quint] =
    qnAuthTraceCodec.++[String, Quint](
      HttpCodec.requestBody(Schema[String]),
    )(appendFifthS)

  private val fuzzEndpoint: Endpoint[String, Quint, String, String, AuthType.None.type] =
    Endpoint(fuzzRoute, fuzzInputCodec, errorCodec, errorCodec, AuthType.None, Doc.empty)

  private def inputOf(c: FuzzCase): Quint =
    (c.q, c.n, c.auth, c.trace, c.body)

  private def checkRoundTrip(c: FuzzCase): TestResult = {
    val input      = inputOf(c)
    val decomposed = EndpointCodecWalker.decompose(fuzzEndpoint, c.slug, input)
    val request    = EndpointBridge.buildRequestPublic(fuzzEndpoint, c.slug, input)
    val decoded    = EndpointCodec.decodeRequest(fuzzEndpoint.input, request)
    val reparsed   = QueryParams.fromEncoded(request.url.queryParams.encode)
    // An empty segment renders as `/fuzz` (the empty fragment is dropped, never
    // root); any non-empty slug renders under `/fuzz/`.
    val pathOk     =
      if (c.slug.isEmpty) request.url.path.encode == "/fuzz"
      else request.url.path.encode.contains("/fuzz/")
    assertTrue(
      decomposed.isRight,
      decomposed.map(_.headers) == Right(Map("Authorization" -> c.auth, "X-Trace" -> c.trace)),
      decoded == Right(input),
      request.url.path != Path.root,
      pathOk,
      request.headers.rawGet("Authorization") == Some(c.auth),
      request.headers.toList.count(_._1.equalsIgnoreCase("Authorization")) == 1,
      request.headers.rawGet("X-Trace") == Some(c.trace),
      request.headers.toList.count(_._1.equalsIgnoreCase("X-Trace")) == 1,
      request.url.queryParams.getFirst("q") == Some(c.q),
      request.url.queryParams.getFirst("n") == Some(c.n.toString),
      reparsed.getFirst("q") == Some(c.q),
      reparsed.getFirst("n") == Some(c.n.toString),
      Schema[String].jsonCodec.decode(request.body.toArray) == Right(c.body),
    )
  }

  def spec = suite("EndpointCodecFuzz")(
    (List(
      test("corpus breadth: at least 50 distinct generated shapes") {
        val distinct = cases.distinct.size
        println("[EndpointCodecFuzzSpec] generated cases=" + cases.size + " distinct=" + distinct)
        assertTrue(cases.size >= 50, distinct >= 50)
      },
      test("query encoding: values are percent-encoded on the wire and stable through render->parse") {
        val request = EndpointBridge.buildRequestPublic(fuzzEndpoint, "plain", ("hello world", 1, "Bearer x", "t", "b"))
        val encoded = request.url.queryParams.encode
        assertTrue(
          !encoded.contains("hello world"),
          encoded.contains("hello%20world"),
          QueryParams.fromEncoded(encoded).getFirst("q") == Some("hello world"),
        )
      },
      test("DUPLICATE-header risk: auth-shaped Bearer header appears exactly once with the exact value") {
        val c         = FuzzCase("plain", "q", 7, "Bearer token with spaces/and+slashes=equals", "t-1", "b")
        val request   = EndpointBridge.buildRequestPublic(fuzzEndpoint, c.slug, inputOf(c))
        val authCount = request.headers.toList.count(_._1.equalsIgnoreCase("Authorization"))
        assertTrue(
          request.headers.rawGet("Authorization") == Some(c.auth),
          authCount == 1,
          EndpointCodec.decodeRequest(fuzzEndpoint.input, request) == Right(inputOf(c)),
        )
      },
      test("special-char risk: query/path/body with reserved chars survive render and parse") {
        val c       = FuzzCase("a b", "a&b=c?d ünï", 3, "Bearer a b/c+d=e", "x y", "say \"hi\"\nbye")
        val request = EndpointBridge.buildRequestPublic(fuzzEndpoint, c.slug, inputOf(c))
        val encoded = request.url.queryParams.encode
        assertTrue(
          !encoded.contains(" "),
          QueryParams.fromEncoded(encoded).getFirst("q") == Some(c.q),
          request.url.path != Path.root,
          EndpointCodec.decodeRequest(fuzzEndpoint.input, request) == Right(inputOf(c)),
        )
      },
      test("repeated query keys decode to the walker first value, never a shadow") {
        val c     = FuzzCase("plain", "first", 9, "Bearer dup", "t", "b")
        val base  = EndpointBridge.buildRequestPublic(fuzzEndpoint, c.slug, inputOf(c))
        val duped = base.copy(url = base.url.copy(queryParams = base.url.queryParams.add("q", "shadow")))
        assertTrue(
          duped.url.queryParams.getFirst("q") == Some("first"),
          EndpointCodec.decodeRequest(fuzzEndpoint.input, duped) == Right(inputOf(c)),
        )
      },
    ) ++ cases.zipWithIndex.map { case (c, i) =>
      test("generated case #" + i + " round-trips decompose->buildRequest->decodeRequest") {
        checkRoundTrip(c)
      }
    }): _*,
  )
}
