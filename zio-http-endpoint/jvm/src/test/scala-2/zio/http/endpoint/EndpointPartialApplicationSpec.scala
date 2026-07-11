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

/**
 * `.implement` classifies handler parameters by type: a parameter typed as the
 * whole `Input` is decoded from the wire; any other nominal-typed parameter is
 * a context requirement tracked in `Route[Ctx]`. There is no by-field-name
 * partial application — a case-class `Input` is passed as one whole value.
 */
object EndpointPartialApplicationSpec extends ZIOSpecDefault {

  final case class Order(orderId: Int, note: String)
  private implicit val orderSchema: Schema[Order] = Schema.derived[Order]

  private val orderEndpoint: Endpoint[Unit, Order, Int, String, AuthType.None.type] = {
    val pattern     = RoutePattern(Method.POST, Path.root)
    val inputCodec  = HttpCodec.Body[CodecKind.Request, Order](Schema[Order])
    val errorCodec  = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
    val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
    Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
  }

  private def requestWith(order: Order): Request =
    Request(
      method = Method.POST,
      url = URL.fromPath(Path.root),
      headers = zio.http.Headers.empty,
      body = EndpointCodec.encodeRequestBody(orderEndpoint.input, order),
      version = Version.`HTTP/1.1`,
    )

  def spec = suite("EndpointPartialApplication")(
    test("a single whole-Input parameter is decoded from the wire and passed to the handler") {
      val route    = orderEndpoint.implement { (order: Order) => order.note }
      val response = InProcessDispatcher.dispatch(route, requestWith(Order(1, "ship it")))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(orderEndpoint.output, response) == Right("ship it"),
      )
    },
    test("a parameter whose type is NOT the Input is a context requirement tracked in Route[Ctx]") {
      final case class Clock(now: Long)
      val route: zio.http.Route[Clock] =
        orderEndpoint.implement { (order: Order, clock: Clock) => s"${order.note}@${clock.now}" }
      val response = InProcessDispatcher.dispatchWith(route, requestWith(Order(1, "note")), Context(Clock(7L)))
      assertTrue(
        response.status == Status.Ok,
        EndpointCodec.decodeResponse(orderEndpoint.output, response) == Right("note@7"),
      )
    },
    test("a context-requiring route is NOT dispatchable with an empty context") {
      assertZIO(
        typeCheck("""
          final case class Clock(now: Long)
          val route =
            orderEndpoint.implement { (order: Order, clock: Clock) => clock.now.toString }
          InProcessDispatcher.dispatchWith(route, requestWith(Order(1, "n")), zio.blocks.context.Context.empty)
        """),
      )(isLeft)
    },
  )
}
