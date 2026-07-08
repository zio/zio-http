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

import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.http.{Header, Headers, Method, Path, Request, Status, URL, Version}
import zio.http.endpoint._

/**
 * Endpoint with a non-trivial `Auth <: AuthType` (`AuthType.Basic`).
 *
 * `AuthType.Basic.unauthorizedStatus` defaults to `Status.NotFound` (404),
 * confirmed by decompiling zio-blocks-endpoint's `AuthType.scala`
 * (`unauthorizedStatus` returns `Status.NotFound`'s int code) -- chosen for
 * information hiding rather than a `401`/`403`.
 *
 * IMPORTANT / REAL BEHAVIOR FINDING: `EndpointSyntaxMacros.implementImpl` (see
 * `EndpointSyntax.scala`) generates a handler that decodes `endpoint.input` and
 * dispatches straight to the user handler; it never reads `endpoint.auth` at
 * all (same real behavior as the Scala 3 bridge). `Auth` is therefore currently
 * a phantom/documentation-only type parameter with NO runtime enforcement. This
 * spec asserts on that REAL, observed behavior (a request without any
 * credentials is NOT rejected today) rather than assuming enforcement exists.
 * This is a real gap, not a main-source bug this task fixes -- see the task's
 * final report.
 */
object EndpointAuthSpec extends ZIOSpecDefault {

  private val secureEndpoint: Endpoint[Unit, String, Int, String, AuthType.Basic.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root / "secure")
    val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.Basic, Doc.empty)
  }

  private val secureRoute = secureEndpoint.implement { (name: String) =>
    s"hello, $name"
  }

  private def requestWith(headers: Headers): Request =
    Request(
      method = Method.POST,
      url = URL.fromPath(Path.root / "secure"),
      headers = headers,
      body = EndpointCodec.encodeRequestBody(secureEndpoint.input, "world"),
      version = Version.`HTTP/1.1`,
    )

  def spec = suite("EndpointAuth")(
    test("AuthType.Basic.unauthorizedStatus defaults to Status.NotFound (information hiding)") {
      assertTrue(secureEndpoint.auth.unauthorizedStatus == Status.NotFound)
    },
    test("REAL BEHAVIOR: a request WITHOUT auth credentials is NOT rejected by .implement today") {
      val response = InProcessDispatcher.dispatch(secureRoute, requestWith(Headers.empty))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(secureEndpoint.output, response) == Right("hello, world"),
      )
    },
    test("a request WITH correct Basic credentials also succeeds (unaffected either way)") {
      val withCreds = Headers.empty.add(Header.Authorization.Basic("alice", "secret"))
      val response  = InProcessDispatcher.dispatch(secureRoute, requestWith(withCreds))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(secureEndpoint.output, response) == Right("hello, world"),
      )
    },
  )
}
