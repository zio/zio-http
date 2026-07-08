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

import zio.test.*

import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.http.Method
import zio.http.Path

/**
 * Full round-trip through REAL HTTP encode/decode: `.implement` produces a
 * `Route`, that `Route` is dispatched via an in-process [[zio.http.Client]]
 * ([[InProcessDispatcher.clientFor]] -- this module has no `zio-http-testkit`
 * dependency to reuse, per this task's scope), and `.call` sends a request
 * through that client and decodes the response back into the `Err | Output`
 * union.
 *
 * Both the SUCCESS path (`Output` round-trips) and the ERROR path (`Err`
 * round-trips) are asserted, exercising the real JSON body encode on the way
 * out and the real status-based branch + JSON body decode on the way back
 * (see `EndpointBridge.encodeResult`/`decodeResponse` in `EndpointSyntax.scala`).
 *
 * NOTE (real-behavior finding, reported rather than fixed per this task's
 * scope): `EndpointBridge.buildRequest` (client side of `.call`, in
 * `EndpointSyntax.scala`) hardcodes `url = URL.root` and never uses
 * `endpoint.route`'s actual path. This means `.call` currently only works
 * correctly for endpoints mounted at the root path "/"; a non-root path
 * (e.g. "/divide") causes a real 404-style route-pattern mismatch. This
 * endpoint's `RoutePattern` therefore intentionally uses the root path here
 * so this test exercises the real, currently-working `.call` behavior; see
 * this task's final report for the bug detail.
 */
object EndpointCallRoundtripSpec extends ZIOSpecDefault {

  final case class Division(numerator: Int, denominator: Int) derives Schema

  private val divideEndpoint: Endpoint[Unit, Division, String, Int, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root)
    val inputCodec  = HttpCodec.Body[CodecKind.Request, Division](Schema[Division])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    val outputCodec = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private val divideRoute = divideEndpoint.implement[EndpointResultHandler.Id] { (d: Division) =>
    if (d.denominator == 0) "cannot divide by zero"
    else d.numerator / d.denominator
  }

  private val client = InProcessDispatcher.clientFor(divideRoute)

  def spec = suite("EndpointCallRoundtrip")(
    test(".call round-trips the Output value through real HTTP encode/decode") {
      val result: String | Int = divideEndpoint.call(client, Division(10, 2))
      assertTrue(result == 5)
    },
    test(".call round-trips the Err value through real HTTP encode/decode") {
      val result: String | Int = divideEndpoint.call(client, Division(10, 0))
      assertTrue(result == "cannot divide by zero")
    },
  )
}
