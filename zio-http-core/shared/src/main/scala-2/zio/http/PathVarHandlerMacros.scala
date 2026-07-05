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

package zio.http

import scala.reflect.macros.whitebox

/**
 * Whitebox macro implementation backing [[PathVarHandler.handler]] - WHITEBOX
 * (not blackbox, despite the plan's shorthand wording) because the macro must
 * report a return type MORE PRECISE than its declared `Handler[Any, Any]`
 * signature (the exact `Ctx`/`RequiredVars` computed from `fn`'s own parameter
 * list) - mirrors zio-blocks' own Scala 2.13 precedent for this exact kind of
 * "compute-and-refine-a-type-from-inputs" macro
 * (`PathVarTuples.Combine.concat`, `Tuples.TuplesMacros.tuplesImpl`).
 */
private[http] object PathVarHandlerMacros {

  def handlerImpl[H: c.WeakTypeTag](c: whitebox.Context)(fn: c.Expr[H]): c.Tree = {
    import c.universe._

    val requestType           = typeOf[zio.http.Request]
    val scopeType             = typeOf[zio.blocks.scope.Scope]
    val pathVarCandidateTypes = List(typeOf[Int], typeOf[Long], typeOf[String], typeOf[Boolean], typeOf[java.util.UUID])
    val pathVarSym            = c.mirror.staticClass("zio.blocks.endpoint.PathVar")

    def isPathVarCandidate(tpe: Type): Boolean = pathVarCandidateTypes.exists(_ =:= tpe.dealias)

    // Every real captured PathVar type is drawn from this closed, five-member universe (the
    // only types `zio.blocks.endpoint.SegmentCodec` can ever capture) - so it is a sound,
    // non-arbitrary signal for "leave this parameter open for phase 2 (RouteBinding.->) to
    // resolve against the route pattern's PathVars", vs. "resolve eagerly from Context now".

    def pathVarType(name: String, tpe: Type): Type =
      appliedType(pathVarSym, List(internal.constantType(Constant(name)), tpe))

    // `RequiredVars`' own encoding for exactly one open param is deliberately BARE
    // (`PathVar[N,T]`, not `Tuple1[PathVar[N,T]]` as zio-blocks' own `PathVarTuples`/`OnePathVar`
    // convention would use for its PHANTOM registry): unlike `PathVar` itself, `Tuple1` is a
    // real, non-erased JVM class, so the compiler generates a genuine runtime cast to it for any
    // lambda parameter DECLARED with that type - if the ACTUAL value flowing through at runtime
    // is bare (see `runtimeShapeOf` below), that generated cast throws a real
    // `ClassCastException`. Keeping both encodings bare-for-one avoids that crash; `PV`/`Req`
    // parsing (RouteBindingMacros.parseEntries) already treats "not a TupleN, not Unit" as a
    // single-entry, arity-1 case, so this is a safe, unobservable-to-callers encoding choice.
    def tupleTypeOf(elems: List[Type]): Type = elems match {
      case Nil           => typeOf[Unit]
      case single :: Nil => single
      case many          => appliedType(definitions.TupleClass(many.length), many)
    }

    // The REAL runtime shape used internally to bridge phase 1 (this macro) and phase 2
    // (RouteBinding.arrowImpl): Unit for zero open params, the bare type for exactly one (no
    // Tuple1 wrapper - mirrors how RoutePattern's own real value type `A` is bare, not wrapped,
    // for a single captured segment), a real TupleN for two or more.
    def runtimeShapeOf(elems: List[Type]): Type = elems match {
      case Nil           => typeOf[Unit]
      case single :: Nil => single
      case many          => appliedType(definitions.TupleClass(many.length), many)
    }

    def runtimeAccess(target: Tree, wholeType: Type, index: Int, arity: Int): Tree =
      arity match {
        case 1 => q"$target.asInstanceOf[$wholeType]"
        case _ => q"$target.asInstanceOf[$wholeType].${TermName(s"_${index + 1}")}"
      }

    // Unwrap the tree of the literal function value passed to `handler(...)` - required since
    // parameter NAMES (invisible in the static FunctionN type) can only be read from the AST.
    def asFunctionLiteral(tree: Tree): Function = tree match {
      case fun: Function             => fun
      case Block(Nil, fun: Function) => fun
      case Typed(inner, _)           => asFunctionLiteral(inner)
      case other                     =>
        c.abort(
          other.pos,
          "handler(...) requires a literal function value (e.g. `(id: Int) => ...`) as its argument " +
            "so that parameter names are visible to the macro; a reference to a pre-declared function " +
            "value is not supported.",
        )
    }

    val Function(vparams, _) = asFunctionLiteral(fn.tree)

    sealed trait ParamKind
    case object IsRequest                                   extends ParamKind
    case object IsScope                                     extends ParamKind
    final case class IsOpenPathVar(name: String, tpe: Type) extends ParamKind
    final case class IsContext(tpe: Type)                   extends ParamKind

    val kinds: List[ParamKind] = vparams.map { vp =>
      val name = vp.name.decodedName.toString
      val tpe  = vp.symbol.typeSignature.dealias
      if (tpe =:= requestType) IsRequest
      else if (tpe =:= scopeType) IsScope
      else if (isPathVarCandidate(tpe)) IsOpenPathVar(name, tpe)
      else IsContext(tpe)
    }

    val openParams = kinds.collect { case p: IsOpenPathVar => p }
    val ctxTypes   = kinds.collect { case IsContext(t) => t }.distinct

    val ctxType: Type =
      ctxTypes match {
        case Nil        => typeOf[Any]
        case one :: Nil => one
        case many       => internal.refinedType(many, c.internal.enclosingOwner)
      }

    val requiredVarsPhantomType: Type = tupleTypeOf(openParams.map(p => pathVarType(p.name, p.tpe)))
    val runtimeShapeType: Type        = runtimeShapeOf(openParams.map(_.tpe))
    val openArity                     = openParams.length

    val fnName      = TermName(c.freshName("fn"))
    val requestName = TermName(c.freshName("request"))
    val contextName = TermName(c.freshName("context"))
    val varsName    = TermName(c.freshName("vars"))
    val scopeName   = TermName(c.freshName("scope"))

    var openIndex            = 0
    val callArgs: List[Tree] = kinds.map {
      case IsRequest           => q"$requestName"
      case IsScope             => q"$scopeName"
      case IsContext(tpe)      => q"$contextName.get[$tpe]"
      case IsOpenPathVar(_, _) =>
        val idx = openIndex
        openIndex += 1
        runtimeAccess(q"$varsName", runtimeShapeType, idx, openArity)
    }

    // `Response | Halt` is not a native union type here - it is `ResultType.|` (an `Either`
    // alias with two IMPLICIT conversions, `responseAsResult`/`haltAsResult`). The generated
    // code must import those explicitly (rather than relying on the CALL SITE happening to have
    // `ResultType._` in scope, which ordinary callers of `handler(fn)` cannot be expected to do)
    // so a plain `Response`- or `Halt`-returning `fn` widens correctly to `Response | Halt`.
    val body =
      q"""
        import _root_.zio.http.ResultType._
        val $fnName = ${fn.tree}
        $fnName(..$callArgs)
      """

    // The lambda passed to `Handler.extracted` is declared with `Vars = runtimeShapeType` (the
    // REAL shape flowing through at runtime), never `requiredVarsPhantomType` directly: unlike
    // `PathVar` and `Tuple1`/`TupleN`, whichever CONCRETE (non-Any) type is written as a lambda's
    // own parameter type gets a genuine JVM bridge-method cast inserted by scalac for that
    // parameter, and `requiredVarsPhantomType` (e.g. a bare `PathVar[N,T]`, never instantiated)
    // would never actually match what's passed in, causing a real `ClassCastException` at
    // invocation time. The phantom type is applied ONLY as an outer `.asInstanceOf` on the
    // already-constructed `Handler` value - a zero-cost generic reparameterization (mirrors
    // exactly how `PathCodecOps.++`/`RoutePatternOps./` themselves bridge their own phantom
    // `PathVars` tracking via `.asInstanceOf`, never a value-level cast).
    q"""
      _root_.zio.http.Handler.extracted[$ctxType, $runtimeShapeType](
        (
          $requestName: _root_.zio.http.Request,
          $contextName: _root_.zio.blocks.context.Context[$ctxType],
          $varsName: $runtimeShapeType,
          $scopeName: _root_.zio.blocks.scope.Scope
        ) => $body
      ).asInstanceOf[_root_.zio.http.Handler[$ctxType, $requiredVarsPhantomType]]
    """
  }
}
