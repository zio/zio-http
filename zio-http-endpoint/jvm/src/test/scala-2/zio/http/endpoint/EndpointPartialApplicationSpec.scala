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
  * `.omo/notepads/endpoint-blocks/decisions.md`: this feature is NOT
  * implemented on Scala 3 either, so there is nothing to test there).
  *
  * REAL BEHAVIOR FINDING, empirically reproduced below via `typeCheck`
  * (reported per this task's scope, NOT fixed -- see this task's final
  * report): `.implement`'s public signature is a strict `Function1`
  * (`Input => Err | Output`). For ANY case-class `Input`:
  *   - A handler whose single parameter's type equals an individual FIELD's
  *     type (not the whole `Input`) fails Scala's own function-argument
  *     type check BEFORE the macro even runs (contravariance: `FieldType =>
  *     X` is never a subtype of `Input => X` unless `Input <: FieldType`,
  *     which never holds for a real case-class field).
  *   - A handler whose single parameter's type equals the WHOLE `Input`
  *     type (so it DOES pass Scala's own type check) instead fails inside
  *     `EndpointSyntaxMacros.implementImpl` itself, because the macro
  *     unconditionally tries to match the parameter's NAME against an
  *     individual field name once `Input` is a case class -- and a
  *     whole-value parameter's name essentially never coincides with one of
  *     its own field's names.
  *
  * The net result: partial (or even full, single-parameter) consumption of
  * a case-class `Input`'s fields is unreachable in EVERY shape on Scala
  * 2.13 today -- not merely "the 2-or-more-field case doesn't work" as this
  * task initially assumed. This spec proves BOTH failure shapes directly,
  * with the compiler's own diagnostics, rather than assuming the feature
  * behaves one way or another.
  */
object EndpointPartialApplicationSpec extends ZIOSpecDefault {

  def spec = suite("EndpointPartialApplication")(
    test("consuming ONE individual field (orderId: Int) does NOT compile (Function1 arity/variance rejects it)") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint.EndpointSyntax._
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

          orderEndpoint.implement { (orderId: Int) => Right(orderId.toString) }
        """)
      )(isLeft)
    },
    test("consuming the WHOLE Input by value (order: Order) ALSO does not compile (macro name-vs-field mismatch)") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint.EndpointSyntax._
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

          orderEndpoint.implement { (order: Order) => Right(order.note) }
        """)
      )(isLeft)
    },
    test("a primitive (non-case-class) Input handler, by contrast, DOES compile") {
      assertZIO(
        typeCheck("""
          import zio.blocks.docs.Doc
          import zio.blocks.endpoint.{AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
          import zio.blocks.schema.Schema
          import zio.http.endpoint.EndpointSyntax._
          import zio.http.{Method, Path}

          val stringEndpoint: Endpoint[Unit, String, String, String, AuthType.None.type] = {
            val pattern     = RoutePattern(Method.POST, Path.root / "echo")
            val inputCodec  = HttpCodec.Body[CodecKind.Request, String](Schema[String])
            val errorCodec  = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            val outputCodec = HttpCodec.Body[CodecKind.Response, String](Schema[String])
            Endpoint(pattern, inputCodec, errorCodec, outputCodec, AuthType.None, Doc.empty)
          }
          stringEndpoint.implement { (input: String) => if (input.isEmpty) Left("empty") else Right(input) }
        """)
      )(isRight)
    },
  )
}
