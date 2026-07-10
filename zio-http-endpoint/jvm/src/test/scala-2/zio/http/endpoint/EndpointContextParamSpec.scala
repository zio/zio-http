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
import zio.test.Assertion._

import zio.blocks.context.Context
import zio.blocks.docs.Doc
import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.schema.Schema
import zio.http.{Method, Path, Request, Status, URL, Version}
import zio.http.endpoint._

object EndpointContextParamSpec extends ZIOSpecDefault {

  final case class Greeter(prefix: String)
  final case class Auditor(tag: String)

  private val endpoint: Endpoint[Unit, String, Int, String, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root)
    val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private val oneCtxRoute: zio.http.Route[Greeter] =
    endpoint.implement { (name: String, greeter: Greeter) =>
      s"${greeter.prefix} $name"
    }

  private val twoCtxRoute =
    endpoint.implement { (name: String, greeter: Greeter, auditor: Auditor) =>
      s"${greeter.prefix} $name [${auditor.tag}]"
    }

  private def requestWith(body: String): Request =
    Request(
      method = Method.POST,
      url = URL.fromPath(Path.root),
      headers = zio.http.Headers.empty,
      body = EndpointCodec.encodeRequestBody(endpoint.input, body),
      version = Version.`HTTP/1.1`,
    )

  def spec = suite("EndpointContextParam")(
    test("single context param resolves from Context and Input decodes from wire") {
      val response =
        InProcessDispatcher.dispatchWith(oneCtxRoute, requestWith("world"), Context(Greeter("hello")))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(endpoint.output, response) == Right("hello world"),
      )
    },
    test("multiple context params accumulate and both resolve from Context") {
      val response =
        InProcessDispatcher.dispatchWith(
          twoCtxRoute,
          requestWith("world"),
          Context(Greeter("hi"), Auditor("audit")),
        )
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(endpoint.output, response) == Right("hi world [audit]"),
      )
    },
    test("a context-requiring route is NOT dispatchable with an empty context (requirement is tracked in the type)") {
      assertZIO(
        typeCheck(
          """InProcessDispatcher.dispatchWith(oneCtxRoute, requestWith("x"), zio.blocks.context.Context.empty)""",
        ),
      )(isLeft)
    },
  )
}
