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
import zio.http.{Headers, Method, Path, Request, Status, URL, Version}
import zio.http.endpoint.EndpointSyntax._

/**
  * End-to-end test for `.implement`/`.call` on Scala 2.13 endpoints.
  *
  * IMPORTANT: this file previously held only a plain `object ... { def main
  * ... }` with no `ZIOSpecDefault`, which `zio.test.sbt.ZTestFramework`
  * cannot discover -- `./mill endpoint.jvm[2.13.18].test` ran ZERO tests
  * from it (confirmed: the baseline `test` run for this module printed no
  * "N tests passed" line at all). It is rewritten here as a real,
  * discoverable spec asserting actual dispatch results, not just
  * compilation.
  */
object EndpointImplementSpec extends ZIOSpecDefault {

  // Simple primitive input for testing (avoids schema derivation complexity).
  private val stringEndpoint: Endpoint[Unit, String, String, String, AuthType.None.type] = {
    val pattern      = RoutePattern(Method.POST, Path.root / "echo")
    val inputCodec   = HttpCodec.Body[CodecKind.Request, String](Schema[String])
    val errorCodec   = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    val outputCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private val echoRoute = stringEndpoint.implement { (input: String) =>
    if (input.isEmpty) Left("Input cannot be empty")
    else Right(s"Echo: $input")
  }

  private def request(body: String): Request =
    Request(
      method = Method.POST,
      url = URL.fromPath(Path.root / "echo"),
      headers = Headers.empty,
      body = EndpointCodec.encodeRequestBody(stringEndpoint.input, body),
      version = Version.`HTTP/1.1`,
    )

  def spec = suite("EndpointImplement")(
    test(".implement dispatches a real request and encodes the real Right(Output) response") {
      val response = InProcessDispatcher.dispatch(echoRoute, request("hello"))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(stringEndpoint.output, response) == Right("Echo: hello"),
      )
    },
    test(".implement dispatches a real request and encodes the real Left(Err) response") {
      val response = InProcessDispatcher.dispatch(echoRoute, request(""))
      assertTrue(
        response.status == Status.BadRequest,
        EndpointCodec.decodeResponse(stringEndpoint.error, response) == Right("Input cannot be empty"),
      )
    },
    test(".call round-trips through the in-process client for the SAME endpoint/route") {
      val rootPattern     = RoutePattern(Method.POST, Path.root)
      val rootEndpoint    = stringEndpoint.copy(route = rootPattern)
      val rootRoute       = rootEndpoint.implement { (input: String) =>
        if (input.isEmpty) Left("Input cannot be empty") else Right(s"Echo: $input")
      }
      val client          = InProcessDispatcher.clientFor(rootRoute)
      // See EndpointCallRoundtripSpec's note: `.call`'s `eithers` implicit parameter does not
      // resolve via plain implicit search on Scala 2.13, so it is passed explicitly here.
      implicit val stringStringEithers: Eithers.Eithers.WithOut[String, String, Either[String, String]] =
        Eithers.Eithers.combineEither[String, String]
      val result: Either[String, String] = rootEndpoint.call(client, "world")
      assertTrue(result == Right("Echo: world"))
    },
  )
}
