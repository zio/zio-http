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

import zio.test._
import zio.test.Assertion._

import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.http.{Method, Path}

/**
  * Multi-field `Input`, on the endpoint's WIRE format.
  *
  * REAL BEHAVIOR FINDING (reported per this task's scope, not fixed --
  * see [[EndpointPartialApplicationSpec]] for the full `typeCheck`-verified
  * proof): `.implement` cannot be invoked AT ALL for a multi-field
  * case-class `Input` on Scala 2.13 -- not even with a handler that takes
  * the "full input" as one parameter, contrary to this task's stated
  * assumption that a full-input handler is "the safe baseline that works
  * on both Scala versions". What IS real and tested here instead is the
  * multi-field `Input`'s wire-level round trip through the exact codec
  * functions `.implement`'s generated code itself calls
  * (`EndpointCodec.encodeRequestBody`/`decodeRequest`), proving the JSON
  * schema for a multi-field case class is correct end to end, independent
  * of `.implement`'s current, separately-documented limitation.
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
      val original = UserProfile(7, "nabil", isActive = true)
      val body     = EndpointCodec.encodeRequestBody(profileEndpoint.input, original)
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
    test("`.implement` on a multi-field case-class Input does not compile in any shape (see EndpointPartialApplicationSpec)") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint._
          import zio.http.{Method, Path}

          final case class UserProfile(userId: Int, displayName: String, isActive: Boolean)
          implicit val userProfileSchema: Schema[UserProfile] = Schema.derived[UserProfile]

          val profileEndpoint: Endpoint[Unit, UserProfile, String, UserProfile, AuthType.None.type] = {
            val pattern     = RoutePattern(Method.PUT, Path.root / "profile")
            val inputCodec  = HttpCodec.Body[CodecKind.Request, UserProfile](Schema[UserProfile])
            val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            val outputCodec = HttpCodec.Body[CodecKind.Response, UserProfile](Schema[UserProfile])
            Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
          }

          profileEndpoint.implement { (profile: UserProfile) => profile }
        """)
      )(isLeft)
    },
  )
}
