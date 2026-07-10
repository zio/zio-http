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
import zio.http.{Method, Path}
import zio.http.endpoint._

/**
 * Multi-field case-class `Input`, exercised both at the WIRE level and through
 * `.implement`'s type-classifier: a single handler parameter typed as the whole
 * `Input` is decoded from the wire and passed to the handler, producing a
 * `Route[Any]` (no context requirements).
 */
object EndpointMultiFieldInputSpec extends ZIOSpecDefault {

  final case class UserProfile(userId: Int, displayName: String, isActive: Boolean)
  private implicit val userProfileSchema: Schema[UserProfile] = Schema.derived[UserProfile]

  private val profileEndpoint: Endpoint[Unit, UserProfile, String, UserProfile, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.PUT, Path.root / "profile")
    val inputCodec  = HttpCodec.Body[CodecKind.Request, UserProfile](Schema[UserProfile])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    val outputCodec = HttpCodec.Body[CodecKind.Response, UserProfile](Schema[UserProfile])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  def spec = suite("EndpointMultiFieldInput")(
    test("the full multi-field Input round-trips through the real wire codec (encode then decode)") {
      val original    = UserProfile(7, "nabil", isActive = true)
      val body        = EndpointCodec.encodeRequestBody(profileEndpoint.input, original)
      val fakeRequest = zio.http.Request(
        method = Method.PUT,
        url = zio.http.URL.fromPath(Path.root / "profile"),
        headers = zio.http.Headers.empty,
        body = body,
        version = zio.http.Version.`HTTP/1.1`,
      )
      assertTrue(EndpointCodec.decodeRequest(profileEndpoint.input, fakeRequest) == Right(original))
    },
    test("different field VALUES produce genuinely different encoded bytes (not a constant/stub encoding)") {
      val bodyA = EndpointCodec.encodeRequestBody(profileEndpoint.input, UserProfile(1, "a", isActive = false))
      val bodyB = EndpointCodec.encodeRequestBody(profileEndpoint.input, UserProfile(2, "b", isActive = true))
      assertTrue(bodyA.toArray.toSeq != bodyB.toArray.toSeq)
    },
    test("`.implement` with a single whole-Input parameter decodes and round-trips the multi-field case class") {
      val route    = profileEndpoint.implement { (profile: UserProfile) => profile }
      val original = UserProfile(42, "aria", isActive = true)
      val response = InProcessDispatcher.dispatch(
        route,
        zio.http.Request(
          method = Method.PUT,
          url = zio.http.URL.fromPath(Path.root / "profile"),
          headers = zio.http.Headers.empty,
          body = EndpointCodec.encodeRequestBody(profileEndpoint.input, original),
          version = zio.http.Version.`HTTP/1.1`,
        ),
      )
      assertTrue(
        response.status == zio.http.Status.Ok,
        EndpointCodec.decodeResponse(profileEndpoint.output, response) == Right(original),
      )
    },
  )
}
