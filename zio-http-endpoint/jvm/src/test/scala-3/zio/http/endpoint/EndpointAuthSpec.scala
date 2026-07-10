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

import zio.blocks.context.Context
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.http.{Header, Headers, Method, Middleware, Path, Request, Routes, Status, URL, Version}

/**
 * Authentication in the unified model: the session is a Tier-2 handler
 * parameter (a context requirement tracked in `Route[User]`), resolved by
 * `Middleware.basicAuth` applied via `@@`. The middleware validates credentials
 * and injects the `User` into the context; on failure it short-circuits with
 * `Response.unauthorized` (401) and the handler never runs.
 */
object EndpointAuthSpec extends ZIOSpecDefault {

  final case class User(name: String)

  private val secureEndpoint: Endpoint[Unit, String, Int, String, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root / "secure")
    val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private val secureRoute: zio.http.Route[User] =
    secureEndpoint.implement[EndpointResultHandler.Id] { (name: String, user: User) =>
      s"hello $name, authenticated as ${user.name}"
    }

  private val securedRoutes: Routes[Any] =
    Routes(secureRoute) @@ Middleware.basicAuth[User] { basic =>
      Right(User(basic.username))
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
    test("a request WITHOUT credentials is rejected with 401 by the middleware") {
      val response =
        InProcessDispatcher.dispatchWith(securedRoutes.routes.head, requestWith(Headers.empty), Context.empty)
      assertTrue(response.status == Status.Unauthorized)
    },
    test("a request WITH valid Basic credentials reaches the handler and receives the injected session") {
      val withCreds = Headers.empty.add(Header.Authorization.Basic("alice", "secret"))
      val response  = InProcessDispatcher.dispatchWith(securedRoutes.routes.head, requestWith(withCreds), Context.empty)
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(secureEndpoint.output, response) == Right("hello world, authenticated as alice"),
      )
    },
  )
}
