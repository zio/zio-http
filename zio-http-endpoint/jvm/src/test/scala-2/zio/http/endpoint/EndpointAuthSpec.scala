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
import zio.http.{Header, Headers, Method, Path, Request, Response, Status, URL, Version}
import zio.http.endpoint._

/**
 * Endpoint with a non-trivial `Auth <: AuthType` (`AuthType.Basic`).
 *
 * `.implementAuth` enforces authentication via an implicit
 * [[EndpointAuthHandler]]: it validates credentials from the request and
 * extracts a `Session` passed to the handler. On failure it returns
 * `auth.unauthorizedStatus` (default `Status.NotFound`) and never invokes the
 * handler.
 */
object EndpointAuthSpec extends ZIOSpecDefault {

  private val secureEndpoint: Endpoint[Unit, String, Int, String, AuthType.Basic.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root / "secure")
    val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.Basic, Doc.empty)
  }

  private implicit val basicAuthHandler: EndpointAuthHandler[AuthType.Basic.type, String] =
    EndpointAuthHandler.fromValidation { (request, auth) =>
      EndpointCodec.decodeHeader(
        auth.codec.asInstanceOf[HttpCodec.Header[CodecKind.Request, Header.Authorization.Basic]],
        request,
      ) match {
        case Right(Header.Authorization.Basic(user, _)) => Right(user)
        case _                                          => Left(Response(status = auth.unauthorizedStatus))
      }
    }

  private val secureRoute = secureEndpoint.implementAuth[String] { (session: String, name: String) =>
    Right(s"hello $name, authenticated as $session")
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
    test("a request WITHOUT credentials is rejected with the unauthorized status") {
      val response = InProcessDispatcher.dispatch(secureRoute, requestWith(Headers.empty))
      assertTrue(response.status == Status.NotFound)
    },
    test("a request WITH valid Basic credentials reaches the handler and receives the extracted session") {
      val withCreds = Headers.empty.add(Header.Authorization.Basic("alice", "secret"))
      val response  = InProcessDispatcher.dispatch(secureRoute, requestWith(withCreds))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(secureEndpoint.output, response) == Right("hello world, authenticated as alice"),
      )
    },
  )
}
