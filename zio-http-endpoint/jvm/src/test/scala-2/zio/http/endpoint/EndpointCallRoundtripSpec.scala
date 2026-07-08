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

import scala.language.implicitConversions

import zio.test._

import zio.blocks.combinators.Eithers
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.http.Method
import zio.http.Path
import zio.http.endpoint._

/**
  * Full round-trip through REAL HTTP encode/decode: `.implement` produces a
  * `Route`, that `Route` is dispatched via an in-process [[zio.http.Client]]
  * ([[InProcessDispatcher.clientFor]] -- this module has no `zio-http-testkit`
  * dependency to reuse, per this task's scope), and `.call` sends a request
  * through that client and decodes the response back into the `Err | Output`
  * union (`Either[Err, Output]` on Scala 2.13).
  *
  * The `.implement` handler returns BARE `Err`/`Output` values (no
  * `Left`/`Right`); `Err` (`String`) and `Output` (`Int`) are distinct so the
  * macro's type-directed leaf tagging is unambiguous.
  *
  * Both the SUCCESS path (`Output` round-trips) and the ERROR path (`Err`
  * round-trips) are asserted, exercising the real JSON body encode on the way
  * out and the real status-based branch + JSON body decode on the way back.
  *
  * Input is a plain `Int` (NOT a case class): a case-class `Input` still has no
  * working `.implement` handler shape on Scala 2.13 (see
  * [[EndpointPartialApplicationSpec]] and decisions.md), so a primitive `Input`
  * -- matched by the macro's `handlerParams.length == 1 && htype =:= inputType`
  * branch -- is the only shape that works for the `.call` Err/Output round trip.
  *
  * NOTE (a second, independent real-behavior finding, reported rather than
  * fixed per this task's scope): `EndpointBridge.buildRequest` (client side
  * of `.call`, in `EndpointSyntax.scala`) hardcodes
  * `url = zio.http.URL.root` and never uses `endpoint.route`'s actual path.
  * This means `.call` currently only works correctly for endpoints mounted
  * at the root path "/"; a non-root path (e.g. "/divide") causes a real
  * route-pattern mismatch. This endpoint's `RoutePattern` therefore
  * intentionally uses the root path here so this test exercises the real,
  * currently-working `.call` behavior.
  */
object EndpointCallRoundtripSpec extends ZIOSpecDefault {

  private val reciprocalEndpoint: Endpoint[Unit, Int, String, Int, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root)
    val inputCodec  = HttpCodec.Body[CodecKind.Request, Int](Schema[Int])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    val outputCodec = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private val reciprocalRoute = reciprocalEndpoint.implement { (denominator: Int) =>
    if (denominator == 0) "cannot divide by zero"
    else 100 / denominator
  }

  private val client = InProcessDispatcher.clientFor(reciprocalRoute)

  // NOTE (real-behavior finding): plain implicit search for `.call`'s `eithers` parameter
  // (`Eithers.Eithers.WithOut[Err, Output, Err | Output]`) does NOT resolve on Scala 2.13,
  // even though `Eithers.Eithers.combineEither[Err, Output]` (invoked directly) produces
  // exactly the required refined type. Confirmed interactively: `implicitly[Eithers.Eithers
  // .WithOut[String, Int, Either[String, Int]]]` fails to resolve, while
  // `Eithers.Eithers.combineEither[String, Int]` alone infers the precise
  // `Eithers[String, Int]{type Out = Either[String, Int]}` refinement `.call` needs. This
  // means `.call` cannot be invoked via ordinary implicit resolution on Scala 2.13 at all --
  // every call site must pass the instance explicitly, as done below.
  private implicit val stringIntEithers: Eithers.Eithers.WithOut[String, Int, Either[String, Int]] =
    Eithers.Eithers.combineEither[String, Int]

  def spec = suite("EndpointCallRoundtrip")(
    test(".call round-trips the Output value through real HTTP encode/decode") {
      val result: Either[String, Int] = reciprocalEndpoint.call(client, 4)
      assertTrue(result == Right(25))
    },
    test(".call round-trips the Err value through real HTTP encode/decode") {
      val result: Either[String, Int] = reciprocalEndpoint.call(client, 0)
      assertTrue(result == Left("cannot divide by zero"))
    },
  )
}
