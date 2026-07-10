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
 *   - `.implement(f: Input => Err | Output)` — server-side
 *   - `.call(client, input)` — client-side
 *
 * Brought into scope by a plain `import zio.http.endpoint._` (the implicit
 * conversion lives on the package object, mirroring Scala 3's public top-level
 * `extension`). No internal member needs to be imported.
 *
 * Current, tested behavior (see `.omo/notepads/endpoint-blocks/decisions.md`):
 *   - `.implement` works only when `Input` is a non-case-class
 *     (primitive/opaque) type; the handler takes the complete `Input` value
 *     directly. For a case-class `Input` there is NO working handler shape — a
 *     2+-param lambda is rejected by `Function1` arity before the macro runs, a
 *     field-typed single param fails the same `Function1` contravariance check,
 *     and a whole-`Input` single param fails inside the macro's name-vs-field
 *     match. Partial parameter application and the `.unused` marker are
 *     therefore NOT functional today.
 *   - The handler returns the bare `Err` value or bare `Output` value directly
 *     (e.g. `if (cond) "error" else 42`); the `Err | Output` union is
 *     represented internally as `Either[Err, Output]` (see the package object's
 *     `type |`), and the macro dispatches on that representation invisibly —
 *     the user never writes `Left`/`Right`/`Either` by hand.
 */
class EndpointSyntax[PathInput, Input, Err, Output, Auth <: AuthType](
  private val endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
) {

  /**
   * Turns a handler function into a `Route[Any]`.
   *
   * The handler receives the complete `Input` value and returns either the bare
   * `Err` value or the bare `Output` value directly — e.g.
   * `input => if (cond) "error" else 42` — with NO `Left`/`Right`/`Either` in
   * the user's code. The macro type-checks each return-position expression of
   * the handler body against `Err` and `Output` and injects the internal
   * `Either` tagging invisibly, then decodes the request body, runs the
   * handler, and encodes the result (error → 400, output → 200).
   *
   * The handler's declared return type is `Any` at the syntactic level (the
   * macro recovers the real `Err`/`Output` shape per branch); this is what lets
   * the user write raw values without wrapping. Only a non-case-class `Input`
   * is supported today (see the class Scaladoc and decisions.md for the
   * case-class limitation).
   */
  def implement(f: Any): Route[Any] =
    macro EndpointSyntaxMacros.implementImpl[PathInput, Input, Err, Output, Auth]

  /**
   * Like [[implement]], but enforces authentication first: the implicit
   * [[EndpointAuthHandler]] validates credentials from the request and extracts
   * a `Session`, which is passed to the handler alongside the decoded `Input`.
   * On authentication failure the endpoint's `auth.unauthorizedStatus` response
   * is returned and the handler is never invoked.
   */
  def implementAuth[Session](
    handler: (Session, Input) => Either[Err, Output],
  )(implicit authHandler: EndpointAuthHandler[Auth, Session]): Route[Any] =
    EndpointServer.implementAuth(endpoint, handler, authHandler)

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
 * Scala 2 blackbox macro for `.implement`.
 *
 * NOTE: the field-matching / `.unused` / 4-combination-warning logic below was
 * written toward a partial-application feature that is NOT functional today —
 * for a case-class `Input` no handler shape reaches it (rejected earlier by
 * `Function1` arity/variance, or aborted by the name-vs-field match). It only
 * runs its single-parameter, whole-`Input` branch for a non-case-class `Input`
 * (`handlerParams.length == 1 && htype =:= inputType`), which is the one
 * supported shape. See `.omo/notepads/endpoint-blocks/decisions.md` for the
 * full `typeCheck`-verified analysis of the case-class limitation.
 */
private[endpoint] object EndpointSyntaxMacros {

  def implementImpl[
    PathInput: c.WeakTypeTag,
    Input: c.WeakTypeTag,
    Err: c.WeakTypeTag,
    Output: c.WeakTypeTag,
    Auth <: AuthType: c.WeakTypeTag,
  ](
    c: blackbox.Context,
  )(f: c.Tree): c.Expr[Route[Any]] = {
    import c.universe._

    val inputType  = weakTypeOf[Input].dealias
    val errType    = weakTypeOf[Err].dealias
    val outputType = weakTypeOf[Output].dealias
    val endpoint   = c.prefix.tree match {
      case Apply(_, List(e)) => e
      case other             => c.abort(c.enclosingPosition, s"Unexpected endpoint extraction: $other")
    }

    val unusedSymbol = typeOf[zio.http.endpoint.Unused[_]].typeSymbol

    def extractInputFields(): List[(String, Type, Boolean)] = {
      try {
        val sym = inputType.typeSymbol
        if (sym != null && sym.isClass && sym.asClass.isCaseClass) {
          val classSym    = sym.asClass
          val constructor = classSym.primaryConstructor.asMethod
          constructor.paramLists.flatten.map { param =>
            val fname         = param.name.toString
            val paramTypeInfo = param.typeSignature
            val paramType     = paramTypeInfo.asSeenFrom(inputType, classSym).dealias
            val isUnused      = paramType.typeSymbol == unusedSymbol
            val actualType    = if (isUnused) paramType.typeArgs.head else paramType
            (fname, actualType, isUnused)
          }
        } else {
          Nil
        }
      } catch {
        case _: Exception => Nil
      }
    }

    def extractHandlerParams(handlerTree: c.Tree): List[(String, Type)] = {
      try {
        handlerTree match {
          case Function(params, _) =>
            params.map { param =>
              val fname = param.name.toString
              val ftype = if (param.tpt == null || param.tpt.isEmpty) NoType else param.tpt.tpe.dealias
              (fname, ftype)
            }
          case _                   =>
            c.abort(c.enclosingPosition, "Handler must be a function literal, e.g. (x: Int, y: String) => ...")
        }
      } catch {
        case e: Exception =>
          c.abort(c.enclosingPosition, s"Failed to extract handler parameters: ${e.getMessage}")
      }
    }

    val inputFields   = extractInputFields()
    val handlerParams = extractHandlerParams(f)
    val inputName     = TermName(c.freshName("decodedInput"))

    if (inputFields.isEmpty && handlerParams.nonEmpty) {
      if (handlerParams.length == 1) {
        c.warning(c.enclosingPosition, "Input is not a case class; assuming single parameter matches full Input")
      } else {
        c.abort(
          c.enclosingPosition,
          "Cannot extract Input fields from non-case-class type with multiple handler parameters",
        )
      }
    }

    val consumed    = Array.fill(inputFields.length)(false)
    val accessExprs = scala.collection.mutable.ListBuffer.empty[(String, Tree)]
    val paramOrder  = scala.collection.mutable.ListBuffer.empty[String]

    if (inputFields.isEmpty && handlerParams.length == 1) {
      val (hname, htype) = handlerParams.head
      if (htype =:= inputType) {
        accessExprs += ((hname, Ident(inputName)))
        paramOrder += hname
      } else {
        c.abort(
          c.enclosingPosition,
          s"Handler parameter type $htype does not match input type $inputType",
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
            val inameIdent           = Ident(TermName(iname))
            val unwrapExpr           = if (isUnused) {
              q"$inameIdent.value"
            } else {
              inameIdent
            }
            accessExprs += ((hname, unwrapExpr))
            paramOrder += hname

          case None =>
            c.abort(
              c.enclosingPosition,
              s"No input field named `$hname` of type $htype is declared by this endpoint input",
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
        "Handler must declare at least one parameter matching an endpoint input field",
      )
    }

    val extractionExprs = accessExprs.map { case (_, expr) => expr }

    // --- Raw-value dispatch (no Left/Right in user code) --------------------
    //
    // The user handler returns a BARE `Err` value or BARE `Output` value from
    // each branch (e.g. `if (cond) "error" else 42`); its declared syntactic
    // return type is `Any`. To recover the `Either[Err, Output]` tagging the
    // downstream response-encoding needs, we wrap every return-position leaf of
    // the handler body in a call to the `EndpointInject` selector. That selector
    // is an implicit resolved at the real (in-scope) typecheck, so a leaf like
    // `input.length` — which only typechecks where the lambda param is bound —
    // is classified correctly. Two ambient instances (`fromErr`/`fromOutput`)
    // map the leaf's own static type to `Left`/`Right`; leaf-local tagging means
    // a branching body whose overall inferred type is the LUB (often `Any`) is
    // handled correctly without ever relying on the whole-body type.
    val errTypeTree    = tq"${errType}"
    val outputTypeTree = tq"${outputType}"

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

    val (handlerParamDef, handlerBody) = f match {
      case Function(List(param), body) => (param, body)
      case Function(params, _)         =>
        c.abort(c.enclosingPosition, s"Handler must declare exactly one parameter, found ${params.length}")
      case other                       =>
        c.abort(c.enclosingPosition, s"Handler must be a function literal, found: $other")
    }
    val rewrittenBody = q"(${rewriteReturns(handlerBody)}): scala.util.Either[$errTypeTree, $outputTypeTree]"
    val taggedHandler                  = Function(List(handlerParamDef), rewrittenBody)

    c.Expr[Route[Any]](
      q"zio.http.endpoint.EndpointServer.implement($endpoint, $taggedHandler)",
    )
  }
}
