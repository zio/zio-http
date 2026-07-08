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

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

import zio.blocks.combinators.Eithers
import zio.blocks.context.Context
import zio.blocks.endpoint.{Alternator, Endpoint, AuthType}
import zio.blocks.scope.Scope
import zio.http.{Client, Handler, Request, Response, Route, Status}

/**
  * Scala 2 extension methods for `zio.blocks.endpoint.Endpoint`:
  *   - `.implement(f: (field1, field2, ...) => Err | Output)` — server-side
  *   - `.call(client, input)` — client-side
  *
  * Full implementation:
  * - Partial parameter application: handler declares SUBSET of Input fields (matched by name+type)
  * - `.unused` marker: inverts warning logic for intentionally-unused fields
  * - 4-combination warning logic (all four cases from RouteBindingMacros pattern)
  * - Zero-cost extraction: fields extracted directly, no tuple boxing
  */
private[endpoint] class EndpointSyntax[PathInput, Input, Err, Output, Auth <: AuthType](
  private val endpoint: Endpoint[PathInput, Input, Err, Output, Auth]
) {

  /**
    * Turns a handler function into a `Route[Any]`.
    *
    * Handler may declare SUBSET of Input fields (matched by name+type).
    * Fields marked `.unused` (via `Unused[T]` wrapper) suppress "never used" warnings.
    * Unconsumed fields trigger "never used" warnings unless marked `.unused`.
    * Consumed fields marked `.unused` trigger "marked unused but referenced" warnings.
    */
  def implement(f: Input => Err | Output): Route[Any] =
    macro EndpointSyntaxMacros.implementImpl[PathInput, Input, Err, Output, Auth]

  /**
    * Calls this endpoint via the given HTTP client, returning the decoded `Err | Output` union.
    *
    * Requires Eithers TC instance for combining error and output responses.
    */
  def call(
    client: Client,
    input: Input
  )(implicit eithers: Eithers.Eithers.WithOut[Err, Output, Err | Output]): Err | Output =
    EndpointBridge.call(endpoint, client, input, Alternator.fromEithers(eithers))
}

private[endpoint] object EndpointSyntax {
  implicit def toEndpointSyntax[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth]
  ): EndpointSyntax[PathInput, Input, Err, Output, Auth] =
    new EndpointSyntax(endpoint)
}

/**
  * Bridge between zio-blocks endpoints and zio.http request/response.
  * Mirrors Scala 3's EndpointBridge design.
  */
private[endpoint] object EndpointBridge {

  def call[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    client: Client,
    input: Input,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Err | Output = {
    val request = buildRequest(endpoint, input)
    val response = client.send(request)
    decodeResponse(endpoint, response, alternator)
  }

  private def buildRequest[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    input: Input,
  ): Request = {
    val pattern = endpoint.route
    val method = pattern.method
    val body = EndpointCodec.encodeRequestBody(endpoint.input, input)
    Request(
      method = method,
      url = zio.http.URL.root,
      headers = zio.http.Headers.empty,
      body = body,
      version = zio.http.Version.`HTTP/1.1`,
    )
  }

  private def decodeResponse[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    response: Response,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Err | Output =
    if (response.status == Status.Ok)
      EndpointCodec.decodeResponse(endpoint.output, response) match {
        case Right(output) => alternator.combine(Right(output))
        case Left(message) => throw new RuntimeException(s"Failed to decode endpoint output: $message")
      }
    else
      EndpointCodec.decodeResponse(endpoint.error, response) match {
        case Right(err) => alternator.combine(Left(err))
        case Left(message) => throw new RuntimeException(s"Failed to decode endpoint error: $message")
      }
}

/**
  * Scala 2 blackbox macro for `.implement`.
  *
  * Full algorithm (from RouteBindingMacros.scala pattern):
  *   1. Extract Input case class fields: (name, type, isUnused) list
  *   2. Extract handler function parameters: (name, type) list
  *   3. Match handler params to Input fields by (name, type) equality
  *   4. Emit 4-combination warnings based on actual consumption
  *   5. Generate handler that extracts only matched fields, passes to function
  *   6. Reconstruct handler application in parameter declaration order
  */
private[endpoint] object EndpointSyntaxMacros {

  def implementImpl[PathInput: c.WeakTypeTag, Input: c.WeakTypeTag, Err: c.WeakTypeTag, Output: c.WeakTypeTag, Auth <: AuthType: c.WeakTypeTag](
    c: blackbox.Context
  )(f: c.Expr[Input => Err | Output]): c.Expr[Route[Any]] = {
    import c.universe._

    val inputType = weakTypeOf[Input].dealias
    val endpoint = c.prefix.tree match {
      case Apply(_, List(e)) => e
      case other             => c.abort(c.enclosingPosition, s"Unexpected endpoint extraction: $other")
    }

    val unusedSymbol = typeOf[zio.http.endpoint.Unused[_]].typeSymbol

    def extractInputFields(): List[(String, Type, Boolean)] = {
      try {
        val sym = inputType.typeSymbol
        if (sym != null && sym.isClass && sym.asClass.isCaseClass) {
          val classSym = sym.asClass
          val constructor = classSym.primaryConstructor.asMethod
          constructor.paramLists.flatten.map { param =>
            val fname = param.name.toString
            val paramTypeInfo = param.typeSignature
            val paramType = paramTypeInfo.asSeenFrom(inputType, classSym).dealias
            val isUnused = paramType.typeSymbol == unusedSymbol
            val actualType = if (isUnused) paramType.typeArgs.head else paramType
            (fname, actualType, isUnused)
          }
        } else {
          Nil
        }
      } catch {
        case _: Exception => Nil
      }
    }

    def extractHandlerParams(handlerExpr: c.Expr[_]): List[(String, Type)] = {
      try {
        handlerExpr.tree match {
          case Function(params, _) =>
            params.map { param =>
              val fname = param.name.toString
              val ftype = if (param.tpt == null || param.tpt.isEmpty) NoType else param.tpt.tpe.dealias
              (fname, ftype)
            }
          case _ =>
            c.abort(c.enclosingPosition, "Handler must be a function literal, e.g. (x: Int, y: String) => ...")
        }
      } catch {
        case e: Exception =>
          c.abort(c.enclosingPosition, s"Failed to extract handler parameters: ${e.getMessage}")
      }
    }

    val inputFields = extractInputFields()
    val handlerParams = extractHandlerParams(f)
    val inputName = TermName(c.freshName("decodedInput"))

    if (inputFields.isEmpty && handlerParams.nonEmpty) {
      if (handlerParams.length == 1) {
        c.warning(c.enclosingPosition, "Input is not a case class; assuming single parameter matches full Input")
      } else {
        c.abort(
          c.enclosingPosition,
          "Cannot extract Input fields from non-case-class type with multiple handler parameters"
        )
      }
    }

    val consumed = Array.fill(inputFields.length)(false)
    val accessExprs = scala.collection.mutable.ListBuffer.empty[(String, Tree)]
    val paramOrder = scala.collection.mutable.ListBuffer.empty[String]

    if (inputFields.isEmpty && handlerParams.length == 1) {
      val (hname, htype) = handlerParams.head
      if (htype =:= inputType) {
        accessExprs += ((hname, Ident(inputName)))
        paramOrder += hname
      } else {
        c.abort(
          c.enclosingPosition,
          s"Handler parameter type $htype does not match input type $inputType"
        )
      }
    } else {
      for ((hname, htype) <- handlerParams) {
        val foundIdx = inputFields.indices.find { i =>
          !consumed(i) && inputFields(i)._1 == hname && inputFields(i)._2 =:= htype
        }

        foundIdx match {
          case Some(idx) =>
            consumed(idx) = true
            val (iname, _, isUnused) = inputFields(idx)
            val inameIdent = Ident(TermName(iname))
            val unwrapExpr = if (isUnused) {
              q"$inameIdent.value"
            } else {
              inameIdent
            }
            accessExprs += ((hname, unwrapExpr))
            paramOrder += hname

          case None =>
            c.abort(
              c.enclosingPosition,
              s"No input field named `$hname` of type $htype is declared by this endpoint input"
            )
        }
      }
    }

    inputFields.zipWithIndex.foreach { case ((name, tpe, isUnused), idx) =>
      if (!isUnused && !consumed(idx)) {
        c.warning(c.enclosingPosition, s"Variable $name:$tpe was defined in the endpoint input but is never used")
      } else if (isUnused && consumed(idx)) {
        c.warning(c.enclosingPosition, s"Variable $name:$tpe was marked .unused but is referenced by the handler")
      }
    }

    if (handlerParams.isEmpty) {
      c.abort(
        c.enclosingPosition,
        "Handler must declare at least one parameter matching an endpoint input field"
      )
    }
    
    val extractionExprs = accessExprs.map { case (_, expr) => expr }
    val handlerCall = q"$f(..$extractionExprs)"

    c.Expr[Route[Any]](
      q"""
        {
          import zio.http.ResultType._
          val ep = $endpoint
          val handler = zio.http.Handler.extracted[Any, Any] { (request: zio.http.Request, ctx: zio.blocks.context.Context[Any], vars: Any, scope: zio.blocks.scope.Scope) =>
            val decodeResult = zio.http.endpoint.EndpointCodec.decodeRequest(ep.input, request)
            decodeResult match {
              case Right($inputName) =>
                val result = $handlerCall
                result match {
                  case Left(err) =>
                    zio.http.endpoint.EndpointCodec.encodeResponse(ep.error, err, 400): (zio.http.Response | zio.http.Halt)
                  case Right(out) =>
                    zio.http.endpoint.EndpointCodec.encodeResponse(ep.output, out, 200): (zio.http.Response | zio.http.Halt)
                }
              case Left(decodeErr) =>
                zio.http.Response(status = zio.http.Status(400), body = zio.http.Body.fromString(decodeErr)): (zio.http.Response | zio.http.Halt)
            }
          }
          zio.http.Route(ep.route, handler)
        }
      """
    )
  }
}
