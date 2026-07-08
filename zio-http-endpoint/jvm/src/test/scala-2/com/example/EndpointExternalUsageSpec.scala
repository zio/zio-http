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
package com.example

import scala.language.implicitConversions

import zio.test._

import zio.blocks.context.Context
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.blocks.scope.Scope
import zio.http.{Body, Halt, Headers, Method, Path, Request, Response, Status, URL, Version}
import zio.http.endpoint._

/**
 * Regression test for the F1 accessibility fix: from a package OTHER than
 * `zio.http.endpoint`, a plain `import zio.http.endpoint._` (with NO internal
 * member such as `EndpointSyntax`, `EndpointCodec`, or `InProcessDispatcher`)
 * must make `.implement` callable on an `Endpoint`. Before the fix the
 * enrichment class/conversion was `private[endpoint]`, so this file would not
 * compile at all.
 *
 * The request body is built here with the public zio-blocks JSON codec (not the
 * module-private `EndpointCodec`) and dispatched through the public
 * `Route.handler.handle` API, so nothing internal to `zio.http.endpoint` is
 * touched from this package.
 */
object EndpointExternalUsageSpec extends ZIOSpecDefault {

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
      body = Body.fromArray(Schema[String].jsonCodec.encode(body)),
      version = Version.`HTTP/1.1`,
    )

  private def dispatch(req: Request): Response = {
    val vars = lengthRoute.pattern.decode(req.method, req.url.path) match {
      case Right(v)    => v
      case Left(error) => throw new RuntimeException(s"Route pattern did not match: $error")
    }
    lengthRoute.handler.handle(req, Context.empty, vars, Scope.global) match {
      case Left(response)        => response
      case Right(Halt(response)) => response
    }
  }

  def spec = suite("EndpointExternalUsage")(
    test(".implement is callable from com.example via a plain `import zio.http.endpoint._`") {
      val response = dispatch(request("hello"))
      assertTrue(
        response.status == Status.Ok,
        Schema[Int].jsonCodec.decode(response.body.toArray) == Right(5),
      )
    },
  )
}
