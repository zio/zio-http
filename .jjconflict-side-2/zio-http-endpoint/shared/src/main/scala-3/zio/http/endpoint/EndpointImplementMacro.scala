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
import zio.blocks.context.Context
import zio.blocks.endpoint.{Alternator, AuthType, Endpoint}
import zio.blocks.scope.Scope
import zio.http.{Handler, Request, Response, Route}

/**
 * Macro backing the `.implement` extension in [[EndpointSyntax]]. Not a
 * user-facing entry point.
 *
 * Handler parameters are classified by type:
 *   - a parameter whose type is the endpoint's `Input` is decoded from the wire
 *   - `Request` / `Scope` parameters are injected from the runtime
 *   - any other nominal-typed parameter is a context requirement, resolved via
 *     `Context.get` and accumulated into the resulting `Route`'s `Ctx`
 *
 * A missing context capability is discharged by applying `Middleware` with
 * `@@`; there is no ambient accessor, so every requirement is visible in the
 * handler's own parameter list and therefore in `Route[Ctx]`.
 */
private[endpoint] object EndpointImplementMacro {

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
  ): Expr[Route[Nothing]] = {
    import quotes.reflect.*

    val inputTpe   = TypeRepr.of[Input]
    val requestTpe = TypeRepr.of[Request]
    val scopeTpe   = TypeRepr.of[Scope]

    def unwrap(t: Term): Term = t match {
      case Inlined(_, _, inner) => unwrap(inner)
      case Typed(inner, _)      => unwrap(inner)
      case Block(Nil, inner)    => unwrap(inner)
      case _                    => t
    }

    val params = unwrap(handler.asTerm) match {
      case Lambda(ps, _)                              => ps.map(p => (p.name, p.tpt.tpe))
      case Block(List(defdef: DefDef), Closure(_, _)) =>
        defdef.paramss.flatMap {
          case TermParamClause(ps) => ps.map(p => (p.name, p.tpt.tpe))
          case _                   => Nil
        }
      case _                                          => report.errorAndAbort("Handler must be a function literal")
    }

    val contextTypeOf: List[Option[TypeRepr]] = params.map { case (_, tpe) =>
      if (tpe =:= inputTpe || tpe =:= requestTpe || tpe =:= scopeTpe) None else Some(tpe)
    }

    val ctxTypes: List[TypeRepr] = {
      val seen = scala.collection.mutable.ListBuffer.empty[TypeRepr]
      contextTypeOf.flatten.foreach(t => if (!seen.exists(_ =:= t)) seen += t)
      seen.toList
    }

    val ctxType: TypeRepr = ctxTypes match {
      case Nil          => TypeRepr.of[Any]
      case head :: tail => tail.foldLeft(head)((acc, t) => AndType(acc, t))
    }

    ctxType.asType match {
      case '[ctxT] =>
        def buildArg(
          tpe: TypeRepr,
          maybeCtxTpe: Option[TypeRepr],
          inputE: Expr[Input],
          requestE: Expr[Request],
          contextE: Expr[Context[ctxT]],
          scopeE: Expr[Scope],
        ): Term =
          if (tpe =:= inputTpe) inputE.asTerm
          else if (tpe =:= requestTpe) requestE.asTerm
          else if (tpe =:= scopeTpe) scopeE.asTerm
          else
            maybeCtxTpe match {
              case Some(t) =>
                t.asType match {
                  case '[tt] => '{ $contextE.asInstanceOf[Context[tt]].get[tt] }.asTerm
                }
              case None    => report.errorAndAbort(s"Cannot classify handler parameter of type ${tpe.show}")
            }

        val built: Expr[Route[ctxT]] = '{
          val ep                              = $endpoint
          val alternator                      = Alternator.fromUnions($unions)
          val rh                              = $resultHandler
          val httpHandler: Handler[ctxT, Any] =
            Handler.extracted[ctxT, Any] { (request, context, _, scope) =>
              EndpointCodec.decodeRequest(ep.input, request) match {
                case Left(_)      => Response.badRequest
                case Right(input) =>
                  val effect      = ${
                    val argTerms = params.indices.toList.map { i =>
                      val (_, tpe) = params(i)
                      buildArg(tpe, contextTypeOf(i), 'input, 'request, 'context, 'scope)
                    }
                    Apply(Select.unique(unwrap(handler.asTerm), "apply"), argTerms).asExprOf[F[Err | Output]]
                  }
                  val unionResult = rh.run(effect)
                  EndpointBridge.encodeResultPublic(ep, unionResult, alternator)
              }
            }
          Route(ep.route, httpHandler)
        }
        built
    }
  }
}
