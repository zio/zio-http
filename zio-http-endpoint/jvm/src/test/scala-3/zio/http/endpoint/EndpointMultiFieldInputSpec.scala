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
import zio.http.{Headers, Method, Path, Request, Status, URL, Version}

/**
 * Multi-field `Input`, `.implement`-d with a handler taking the FULL input.
 *
 * This is the "safe baseline" per this module's known limitations (see
 * `.omo/notepads/endpoint-blocks/decisions.md`): Scala 3's partial-parameter
 * macro is NOT implemented, so the handler always declares the complete
 * `Input` case class here, never a subset.
 *
 * All three fields are carried in a single JSON request body (rather than
 * split across path/query/body) because `EndpointCodec.decodeRequest`
 * currently only truly decodes `HttpCodec.Body`-shaped codecs from the wire;
 * non-`Body` shapes (`Query`, `Header`, `Combine`) fall through to a
 * best-effort path that returns `()` when no reachable `Body` schema exists
 * (see `EndpointCodec.scala`'s own docstring: "richer shapes ... are a
 * follow-up"). A case class with 2-3 fields decoded as one JSON body is the
 * combination that is actually exercised correctly by the current bridge.
 */
object EndpointMultiFieldInputSpec extends ZIOSpecDefault {

  final case class UserProfile(userId: Int, displayName: String, isActive: Boolean) derives Schema

  private val profileEndpoint: Endpoint[Unit, UserProfile, String, UserProfile, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.PUT, Path.root / "profile")
    val inputCodec  = HttpCodec.Body[CodecKind.Request, UserProfile](Schema[UserProfile])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    val outputCodec = HttpCodec.Body[CodecKind.Response, UserProfile](Schema[UserProfile])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  // Full-input handler: consumes ALL three fields, matching the "safe baseline" documented above.
  private val profileRoute = profileEndpoint.implement[EndpointResultHandler.Id] { (profile: UserProfile) =>
    if (profile.displayName.isEmpty) "displayName must not be empty"
    else profile.copy(displayName = profile.displayName.toUpperCase)
  }

  private def request(profile: UserProfile): Request =
    Request(
      method = Method.PUT,
      url = URL.fromPath(Path.root / "profile"),
      headers = Headers.empty,
      body = EndpointCodec.encodeRequestBody(profileEndpoint.input, profile),
      version = Version.`HTTP/1.1`,
    )

  def spec = suite("EndpointMultiFieldInput")(
    test("full-input handler receives ALL fields correctly decoded from the wire") {
      val response = InProcessDispatcher.dispatch(profileRoute, request(UserProfile(42, "nabil", isActive = true)))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(profileEndpoint.output, response) ==
          Right(UserProfile(42, "NABIL", isActive = true)),
      )
    },
    test("full-input handler sees field values change the outcome (real values, not just compilation)") {
      val response = InProcessDispatcher.dispatch(profileRoute, request(UserProfile(7, "", isActive = false)))
      assertTrue(
        response.status == Status.BadRequest,
        EndpointCodec.decodeResponse(profileEndpoint.error, response) == Right("displayName must not be empty"),
      )
    },
  )
}
