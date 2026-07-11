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
import scala.reflect.macros.whitebox

import zio.blocks.combinators.Eithers
import zio.blocks.endpoint.{Alternator, Endpoint, AuthType}
import zio.http.{Client, Request, Response, Route, Status}

/**
 * Scala 2 extension methods for `zio.blocks.endpoint.Endpoint`:
 *   - `.implement(f: Input => Err | Output)` — server-side
 *   - `.call(client, input)` — client-side
 *
 * Brought into scope by a plain `import zio.http.endpoint._` (the implicit
 * conversion lives on the package object, mirroring Scala 3's public top-level
 * `extension`). No internal member needs to be imported.
 *
 * Behavior (mirroring the Scala 3 side):
 *   - `.implement` classifies the handler's parameters by type: a parameter
 *     typed as the whole `Input` is decoded from the wire, a `Request` /
 *     `Scope` parameter is injected from the runtime, and any other
 *     nominal-typed parameter is a context requirement resolved from the
 *     `Context` and tracked in the resulting `Route[Ctx]`.
 *   - The handler returns the bare `Err` value or bare `Output` value directly
 *     (e.g. `if (cond) "error" else 42`); the `Err | Output` union is
 *     represented internally as `Either[Err, Output]` (see the package object's
 *     `type |`), and the macro dispatches on that representation invisibly —
 *     the user never writes `Left`/`Right`/`Either` by hand. `Err` and `Output`
 *     must be distinct types so a bare value tags unambiguously.
 */
class EndpointSyntax[PathInput, Input, Err, Output, Auth <: AuthType](
  private val endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
) {

  /**
   * Turns a handler function into a `Route[Ctx]`, where `Ctx` is the
   * intersection of every context requirement declared in the handler's
   * parameter list.
   *
   * The handler's parameters are classified by type: a parameter typed as the
   * endpoint's `Input` is decoded from the wire, a `Request` / `Scope`
   * parameter is injected from the runtime, and any other nominal-typed
   * parameter is a context requirement resolved from the `Context` and tracked
   * in `Ctx`. Each branch returns the bare `Err` or `Output` value directly —
   * e.g. `(input, svc) => if (cond) "error" else svc.compute(input)` — with NO
   * `Left`/`Right`/`Either` in the user's code; the macro tags each
   * return-position leaf and encodes the result (error → 400, output → 200).
   *
   * The macro is whitebox so the precise `Route[Ctx]` flows to the call site; a
   * missing context capability is discharged later by applying a `Middleware`
   * with `@@`.
   */
  def implement(f: Any): Route[Nothing] =
    macro EndpointSyntaxMacros.implementImpl[PathInput, Input, Err, Output, Auth]

  /**
   * Calls this endpoint via the given HTTP client, returning the decoded
   * `Err | Output` union.
   *
   * Requires Eithers TC instance for combining error and output responses.
   */
  def call(
    client: Client,
    input: Input,
  )(implicit eithers: Eithers.Eithers.WithOut[Err, Output, Err | Output]): Err | Output =
    EndpointBridge.call(endpoint, client, input, Alternator.fromEithers(eithers))
}

/**
 * Bridge between zio-blocks endpoints and zio.http request/response. Mirrors
 * Scala 3's EndpointBridge design.
 */
private[endpoint] object EndpointBridge {

  def call[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    client: Client,
    input: Input,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Err | Output = {
    val request  = buildRequest(endpoint, input)
    val response = client.send(request)
    decodeResponse(endpoint, response, alternator)
  }

  private def buildRequest[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    input: Input,
  ): Request = {
    val pattern = endpoint.route
    val method  = pattern.method
    val body    = EndpointCodec.encodeRequestBody(endpoint.input, input)
    Request(
      method = method,
      url = zio.http.URL.root, // TODO: extract path from zio.blocks.endpoint.RoutePattern when API is available
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
        case Right(err)    => alternator.combine(Left(err))
        case Left(message) => throw new RuntimeException(s"Failed to decode endpoint error: $message")
      }
}

/**
 * Scala 2 whitebox macro for `.implement`.
 *
 * Handler parameters are classified by TYPE, mirroring the Scala 3
 * [[EndpointImplementMacro]]:
 *   - a parameter whose type is the endpoint's `Input` is decoded from the
 *     wire;
 *   - a `Request` / `Scope` parameter is injected from the runtime;
 *   - any other nominal-typed parameter is a context requirement, resolved via
 *     `Context.get` and accumulated (via `with`) into the resulting `Route`'s
 *     `Ctx`.
 *
 * The macro is WHITEBOX so the precise `Route[Ctx]` flows to the call site (a
 * blackbox macro cannot express a return type refined by the classified context
 * parameters). A missing context capability is later discharged by applying a
 * `Middleware` with `@@`; every requirement is visible in the handler's own
 * parameter list and therefore in `Route[Ctx]`.
 *
 * The handler still returns a BARE `Err`/`Output` value per branch (no
 * `Left`/`Right`); the raw-value [[EndpointInject]] tagging recovers the
 * internal `Either[Err, Output]` union representation.
 */
private[endpoint] object EndpointSyntaxMacros {

  def implementImpl[
    PathInput: c.WeakTypeTag,
    Input: c.WeakTypeTag,
    Err: c.WeakTypeTag,
    Output: c.WeakTypeTag,
    Auth <: AuthType: c.WeakTypeTag,
  ](
    c: whitebox.Context,
  )(f: c.Tree): c.Tree = {
    import c.universe._

    val pathInputType = weakTypeOf[PathInput].dealias
    val inputType     = weakTypeOf[Input].dealias
    val errType       = weakTypeOf[Err].dealias
    val outputType    = weakTypeOf[Output].dealias
    val authType      = weakTypeOf[Auth].dealias
    val requestType   = typeOf[Request].dealias
    val scopeType     = typeOf[zio.blocks.scope.Scope].dealias
    val endpoint      = c.prefix.tree match {
      case Apply(_, List(e)) => e
      case other             => c.abort(c.enclosingPosition, s"Unexpected endpoint extraction: $other")
    }

    val typedParamTypes: List[Type] = f match {
      case Function(params, _) if params.nonEmpty =>
        params.map(p => if (p.tpt == null || p.tpt.isEmpty) NoType else p.tpt.tpe.dealias)
      case Function(_, _)                         =>
        c.abort(c.enclosingPosition, "Handler must declare at least one parameter")
      case other                                  =>
        c.abort(c.enclosingPosition, s"Handler must be a function literal, found: $other")
    }

    val (handlerParamDefs, handlerBody) = c.untypecheck(f.duplicate) match {
      case Function(params, body) => (params, body)
      case other                  =>
        c.abort(c.enclosingPosition, s"Handler must be a function literal, found: $other")
    }

    def paramType(index: Int): Type = typedParamTypes(index)

    val contextTypeOf: List[Option[Type]] = typedParamTypes.map { htype =>
      if (htype =:= inputType || htype =:= requestType || htype =:= scopeType) None
      else Some(htype)
    }

    val ctxTypes: List[Type] = {
      val seen = scala.collection.mutable.ListBuffer.empty[Type]
      contextTypeOf.flatten.foreach(t => if (!seen.exists(_ =:= t)) seen += t)
      seen.toList
    }

    val ctxTypeTree: Tree = ctxTypes match {
      case Nil          => tq"_root_.scala.Any"
      case head :: tail =>
        tail.foldLeft(tq"$head": Tree)((acc, t) => tq"$acc with $t")
    }

    val inputParam   = TermName(c.freshName("input"))
    val requestParam = TermName(c.freshName("request"))
    val contextParam = TermName(c.freshName("context"))
    val scopeParam   = TermName(c.freshName("scope"))

    val argExprs: List[Tree] = contextTypeOf.zipWithIndex.map { case (maybeCtx, index) =>
      maybeCtx match {
        case None    =>
          val htype = paramType(index)
          if (htype =:= inputType) Ident(inputParam)
          else if (htype =:= requestType) Ident(requestParam)
          else Ident(scopeParam)
        case Some(t) =>
          q"$contextParam.asInstanceOf[_root_.zio.blocks.context.Context[$t]].get[$t]"
      }
    }

    // --- Raw-value dispatch (no Left/Right in user code) --------------------
    //
    // The user handler returns a BARE `Err` value or BARE `Output` value from
    // each branch (e.g. `if (cond) "error" else 42`); its declared syntactic
    // return type is `Any`. To recover the `Either[Err, Output]` tagging the
    // downstream response-encoding needs, we wrap every return-position leaf of
    // the handler body in a call to the `EndpointInject` selector. That selector
    // is an implicit resolved at the real (in-scope) typecheck, so a leaf like
    // `input.length` — which only typechecks where the lambda param is bound —
    // is classified correctly.
    val errTypeTree    = tq"$errType"
    val outputTypeTree = tq"$outputType"

    def rewriteReturns(body: Tree): Tree = body match {
      case If(cond, thenp, elsep) => If(cond, rewriteReturns(thenp), rewriteReturns(elsep))
      case Block(stats, expr)     => Block(stats, rewriteReturns(expr))
      case Match(sel, cases)      =>
        Match(sel, cases.map { case CaseDef(pat, guard, cbody) => CaseDef(pat, guard, rewriteReturns(cbody)) })
      case Typed(expr, _)         => rewriteReturns(expr)
      case Annotated(_, expr)     => rewriteReturns(expr)
      case leaf                   =>
        q"zio.http.endpoint.EndpointInject.inject[$errTypeTree, $outputTypeTree]($leaf)"
    }

    val bindings: List[Tree] = handlerParamDefs.zip(argExprs).map { case (param, arg) =>
      q"val ${param.name} = $arg"
    }

    val taggedBody =
      q"""{
        ..$bindings
        (${rewriteReturns(handlerBody)}): _root_.scala.util.Either[$errTypeTree, $outputTypeTree]
      }"""

    q"""
      zio.http.endpoint.EndpointServer.implementCtx[$ctxTypeTree, $pathInputType, $inputType, $errType, $outputType, $authType](
        $endpoint,
        ($inputParam: $inputType, $requestParam: _root_.zio.http.Request, $contextParam: _root_.zio.blocks.context.Context[$ctxTypeTree], $scopeParam: _root_.zio.blocks.scope.Scope) => $taggedBody
      )
    """
  }
}
