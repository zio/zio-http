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
import zio.http.endpoint._

/**
 * End-to-end test for `.implement`/`.call` on Scala 2.13 endpoints.
 *
 * The handler returns BARE `Err`/`Output` values (no `Left`/`Right`/`Either` in
 * user code); the `.implement` macro tags each return-position leaf by its
 * static type. `Err` (`String`) and `Output` (`Int`) are distinct so the
 * type-directed tagging is unambiguous.
 *
 * Only `import zio.http.endpoint._` is imported for the syntax -- no internal
 * `EndpointSyntax` member.
 */
object EndpointImplementSpec extends ZIOSpecDefault {

  private val lengthEndpoint: Endpoint[Unit, String, String, Int, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root / "length")
    val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    val outputCodec = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private val lengthRoute = lengthEndpoint.implement { (input: String) =>
    if (input.isEmpty) "Input cannot be empty"
    else input.length
  }

  private def request(body: String): Request =
    Request(
      method = Method.POST,
      url = URL.fromPath(Path.root / "length"),
      headers = Headers.empty,
      body = EndpointCodec.encodeRequestBody(lengthEndpoint.input, body),
      version = Version.`HTTP/1.1`,
    )

  def spec = suite("EndpointImplement")(
    test(".implement dispatches a real request and encodes the real Output response") {
      val response = InProcessDispatcher.dispatch(lengthRoute, request("hello"))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(lengthEndpoint.output, response) == Right(5),
      )
    },
    test(".implement dispatches a real request and encodes the real Err response") {
      val response = InProcessDispatcher.dispatch(lengthRoute, request(""))
      assertTrue(
        response.status == Status.BadRequest,
        EndpointCodec.decodeResponse(lengthEndpoint.error, response) == Right("Input cannot be empty"),
      )
    },
    test(".call round-trips through the in-process client for the SAME endpoint/route") {
      val rootPattern  = RoutePattern(Method.POST, Path.root)
      val rootEndpoint = lengthEndpoint.copy(route = rootPattern)
      val rootRoute    = rootEndpoint.implement { (input: String) =>
        if (input.isEmpty) "Input cannot be empty" else input.length
      }
      val client       = InProcessDispatcher.clientFor(rootRoute)
      // See EndpointCallRoundtripSpec's note: `.call`'s `eithers` implicit parameter does not
      // resolve via plain implicit search on Scala 2.13, so it is passed explicitly here.
      implicit val stringIntEithers: Eithers.Eithers.WithOut[String, Int, Either[String, Int]] =
        Eithers.Eithers.combineEither[String, Int]
      val result = rootEndpoint.call(client, "world")
      assertTrue(result == Right(5))
    },
  )
}
