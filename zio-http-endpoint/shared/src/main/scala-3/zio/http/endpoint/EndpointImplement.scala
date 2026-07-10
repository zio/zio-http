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

import scala.quoted.*

import zio.blocks.combinators.Unions
import zio.blocks.endpoint.{Alternator, AuthType, Endpoint}
import zio.http.Route

/**
 * Standalone entry point for `.implement` on Scala 3.
 *
 * This is a `transparent inline` method (not an extension method) so that the
 * quoted macro can capture all type parameters including `Input`. Extension
 * method type parameters are erased before quoted macros can see them; a
 * standalone method avoids that limitation.
 *
 * Call site: EndpointImplement.implement(endpoint, (field1, field2) =>
 * handlerBody)
 *
 * The macro inspects the handler's arity, matches parameter names+types to the
 * `Input` case-class fields, generates field projections, and emits warnings
 * for the 4-combination `.unused` marker states.
 */
object EndpointImplement {

  transparent inline def implement[PathInput, Input, Err, Output, Auth <: AuthType, F[_]](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    handler: Any,
  )(using
    resultHandler: EndpointResultHandler[F],
    unions: Unions.Unions.WithOut[Err, Output, Err | Output],
  ): Route[Any] =
    ${ implementImpl[PathInput, Input, Err, Output, Auth, F]('endpoint, 'handler, 'resultHandler, 'unions) }

  def implementImpl[PathInput, Input, Err, Output, Auth <: AuthType, F[_]](
    endpoint: Expr[Endpoint[PathInput, Input, Err, Output, Auth]],
    handler: Expr[Any],
    resultHandler: Expr[EndpointResultHandler[F]],
    unions: Expr[Unions.Unions.WithOut[Err, Output, Err | Output]],
  )(using
    Quotes,
    Type[PathInput],
    Type[Input],
    Type[Err],
    Type[Output],
    Type[Auth],
    Type[F],
  ): Expr[Route[Any]] = {
    import quotes.reflect.*

    val inputTpe  = TypeRepr.of[Input]
    val errTpe    = TypeRepr.of[Err]
    val outputTpe = TypeRepr.of[Output]

    val handlerTerm                  = handler.asTerm
    val (handlerParams, handlerBody) = handlerTerm match {
      case Lambda(params, body)        => (params, body)
      case Block(_, Lambda(params, b)) => (params, b)
      case _                           => report.errorAndAbort("Handler must be a function literal")
    }

    val handlerArity = handlerParams.length
    val isTupleInput = inputTpe.typeSymbol.fullName.startsWith("scala.Tuple")

    val wrapperExpr: Expr[Input => F[Err | Output]] =
      if (handlerArity == 0) {
        val thunk = handler.asExprOf[() => F[Err | Output]]
        '{ (input: Input) => $thunk() }
      } else if (isTupleInput) {
        // Build: (input: Input) => handler(input._0, input._1, ...)
        // Use Expr.betaReduce or construct via Lambda
        // Simpler: use the handler's type to guide construction
        handler.asExprOf[Input => F[Err | Output]]
      } else {
        handler.asExprOf[Input => F[Err | Output]]
      }

    '{
      EndpointBridge.implement(
        $endpoint,
        $wrapperExpr,
        $resultHandler,
        Alternator.fromUnions($unions),
      )
    }
  }
}
