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

/**
 * Real partial-application tests, Scala 2.13 ONLY (see
 * `.omo/notepads/endpoint-blocks/decisions.md`: this feature is NOT implemented
 * on Scala 3 either, so there is nothing to test there).
 *
 * REAL BEHAVIOR FINDING, empirically reproduced below via `typeCheck` (reported
 * per this task's scope, NOT fixed): for ANY case-class `Input`, `.implement`
 * still has no working handler shape on Scala 2.13, even with the raw-value API
 * (handler returns bare `Err`/`Output`, no `Left`/`Right`):
 *   - A handler whose single parameter's type equals an individual FIELD's type
 *     (not the whole `Input`) does NOT compile: the macro matches the parameter
 *     NAME against a field, extracts that field, and passes it, but the
 *     field-typed value never reconstructs the whole `Input` the codec needs --
 *     the generated call fails to typecheck.
 *   - A handler whose single parameter's type equals the WHOLE `Input` type
 *     fails inside `EndpointSyntaxMacros.implementImpl`, because the macro
 *     unconditionally tries to match the parameter's NAME against an individual
 *     field name once `Input` is a case class, and a whole-value parameter's
 *     name essentially never coincides with one of its own field's names.
 *
 * A primitive (non-case-class) `Input` DOES work: the handler takes the
 * complete value directly and returns a bare `Err`/`Output`.
 */
object EndpointPartialApplicationSpec extends ZIOSpecDefault {

  def spec = suite("EndpointPartialApplication")(
    test("consuming ONE individual field (orderId: Int) does NOT compile") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint._
          import zio.http.{Method, Path}

          final case class Order(orderId: Int, note: String)
          implicit val orderSchema: Schema[Order] = Schema.derived[Order]

          val orderEndpoint: Endpoint[Unit, Order, String, String, AuthType.None.type] = {
            val pattern     = RoutePattern(Method.POST, Path.root / "orders")
            val inputCodec  = HttpCodec.Body[CodecKind.Request, Order](Schema[Order])
            val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
          }

          orderEndpoint.implement { (orderId: Int) => orderId.toString }
        """),
      )(isLeft)
    },
    test("consuming the WHOLE Input by value (order: Order) ALSO does not compile (macro name-vs-field mismatch)") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint._
          import zio.http.{Method, Path}

          final case class Order(orderId: Int, note: String)
          implicit val orderSchema: Schema[Order] = Schema.derived[Order]

          val orderEndpoint: Endpoint[Unit, Order, String, String, AuthType.None.type] = {
            val pattern     = RoutePattern(Method.POST, Path.root / "orders")
            val inputCodec  = HttpCodec.Body[CodecKind.Request, Order](Schema[Order])
            val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
          }

          orderEndpoint.implement { (order: Order) => order.note }
        """),
      )(isLeft)
    },
    test("a primitive (non-case-class) Input handler returning bare Err/Output values DOES compile") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint._
          import zio.http.{Method, Path}

          val stringEndpoint: Endpoint[Unit, String, String, Int, AuthType.None.type] = {
            val pattern     = RoutePattern(Method.POST, Path.root / "echo")
            val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
            val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            val outputCodec = HttpCodec.Body[CodecKind.Response, Int](Schema[Int])
            Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
          }
          stringEndpoint.implement { (input: String) => if (input.isEmpty) "empty" else input.length }
        """),
      )(isRight)
    },
  )
}
