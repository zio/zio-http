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

import zio.blocks.context.Context
import zio.blocks.endpoint.{PathVar, RoutePattern}
import zio.blocks.scope.Scope

import scala.quoted._

/**
 * Scala 3 implementation of the FINAL two-phase `pattern -> handler(fn)` design (D9/D12 in
 * `.omo/drafts/route-pattern-typed-vars.md`).
 *
 * ==Why this is ONE macro pass triggered at `->`, not two independently-typed phases==
 *
 * The draft describes `handler(fn)` as returning a precisely-typed `Handler[Ctx, RequiredVars]`
 * that `->` then matches against the pattern by INSPECTING THAT TYPE. That is not implementable
 * as written on Scala 3: `handler`'s precise return type is only known after inlining it (it is a
 * `transparent inline` macro), but Scala resolves which `->` overload/extension applies to its
 * result BEFORE forcing that inlining, using `handler`'s non-transparent declared type (`Any`) for
 * the applicability check. Every overload of `->` requiring `h: Handler[Ctx, Req]` therefore fails
 * to apply to a `handler(fn)` argument, and calls silently fall back to `scala.Predef.ArrowAssoc`
 * (building a plain `(RoutePattern, Handler)` tuple instead of a `Route`) - a real, reproduced
 * failure mode during this implementation, not a hypothetical one.
 *
 * The fix: `->` is a SINGLE macro over an unconstrained `inline h: Any` parameter (so it always
 * applies, and - being an explicit-import extension - takes priority over `Predef.ArrowAssoc`
 * regardless of `h`'s shape). It inspects `h`'s raw syntax tree: if `h` is itself a call to
 * [[RouteBinding.handler]], `->` extracts the ORIGINAL function literal from that call and runs
 * the full name/type classification (D7) and pattern-matching (D6) in one pass, directly building
 * the final `Handler[Ctx, A]`. Otherwise `h` is treated as an already-built `Handler[Ctx, A]`
 * value (e.g. `Handler.succeed(...)`) and passed straight through to `Route.apply`, unmodified.
 * [[RouteBinding.handler]] itself remains a correct, independently-typed `transparent inline`
 * macro for any OTHER (non-`->`) use of its result.
 *
 * ==Why the Context-vs-PathVar decision is safe without seeing the pattern==
 *
 * For every parameter of `fn` that is not `Request`/`Scope`-typed, the classifier must decide
 * whether it is an open path-variable requirement (matched against the pattern below) or a
 * `Context`-tier capability (resolved via `Context[Ctx].get[T]`, D7 tier 2). This is decidable
 * without the pattern because zio-blocks' `SegmentCodec` can only ever capture a path variable as
 * one of exactly five primitive types: `Int`, `Long`, `String`, `Boolean`, `java.util.UUID` (see
 * `SegmentCodec.int`/`long`/`string`/`bool`/`uuid`). A parameter typed as one of those five is
 * therefore ALWAYS a path-variable candidate (it can never legitimately be a `Context` capability,
 * since `PathVar`'s own `Type` parameter is restricted to that same set); a parameter typed as
 * anything else (e.g. a custom `BasketId`) can NEVER be a path variable, so it is ALWAYS resolved
 * from `Context`. A path-variable candidate that the pattern does not provide is unsatisfiable (no
 * further `Context` fallback applies to it) and is reported as a compile error at the `->` call
 * site - this is the plan's required negative-test behavior.
 */
object RouteBinding {

  /**
   * Reads `fn`'s parameter names and types via `quotes.reflect`, resolves `Request`/`Scope`-typed
   * parameters (D7 tier 3) and any parameter whose type is NOT one of the five path-var-eligible
   * primitives (D7 tier 2, via `Context[Ctx].get[T]`) EAGERLY, independent of any route pattern,
   * and leaves every remaining (path-var-eligible-typed) parameter as an open `PathVar[Name,Type]`
   * entry in the returned `Handler`'s `Vars` tuple type, in `fn`'s own declared parameter order.
   * When combined immediately with `->` (as in every worked example), `->` bypasses this
   * intermediate `Handler` entirely and re-derives the same classification directly against the
   * pattern in one pass (see the module doc above) - this method also stands on its own for any
   * other use of a name+type-classified `Handler`.
   */
  transparent inline def handler[H](inline fn: H): Any =
    ${ RouteBindingMacros.handlerImpl[H]('fn) }

  extension [A, PV](pattern: RoutePattern[A] { type PathVars = PV }) {

    /**
     * Builds a `Route[Ctx]` from `pattern` and `h`. If `h` is a `handler(fn)` call, matches `fn`'s
     * open path-variable requirements against `PV` by (name, type), in any order (D6), rewires
     * each match to direct positional access into the pattern's real runtime value tuple, warns
     * (via `quotes.reporting.warning`) on every `PV` entry no requirement consumes (D4), and
     * raises a compile error if a requirement has no match. Otherwise `h` is treated as an
     * already-built `Handler[Ctx, A]` value (e.g. `Handler.succeed(...)`) and passed straight
     * through to the existing `Route.apply`.
     */
    transparent inline def ->(inline h: Any): Route[Nothing] =
      ${ RouteBindingMacros.arrowImpl[A, PV]('pattern, 'h) }
  }
}

private[http] object RouteBindingMacros {

  private def isPathVarEligible(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._
    tpe =:= TypeRepr.of[Int] || tpe =:= TypeRepr.of[Long] || tpe =:= TypeRepr.of[Boolean] ||
    tpe =:= TypeRepr.of[String] || tpe =:= TypeRepr.of[java.util.UUID]
  }

  private def unwrapTerm(using q: Quotes)(t: q.reflect.Term): q.reflect.Term = {
    import q.reflect._
    t match {
      case Inlined(_, _, inner) => unwrapTerm(inner)
      case Typed(inner, _)      => unwrapTerm(inner)
      case Block(Nil, inner)    => unwrapTerm(inner)
      case _                    => t
    }
  }

  private def extractParams(using q: Quotes)(term: q.reflect.Term): List[(String, q.reflect.TypeRepr)] = {
    import q.reflect._

    def paramsOf(defdef: DefDef): List[(String, TypeRepr)] =
      defdef.paramss.flatMap {
        case TermParamClause(params) => params.map(p => (p.name, p.tpt.tpe))
        case _                       => Nil
      }

    unwrapTerm(term) match {
      case Block(List(defdef: DefDef), Closure(_, _)) => paramsOf(defdef)
      case Block(stats, Closure(_, _)) =>
        stats.collectFirst { case defdef: DefDef => defdef } match {
          case Some(defdef) => paramsOf(defdef)
          case None         =>
            report.errorAndAbort("handler(...) requires a plain function literal, e.g. handler((id: Int) => ...)")
        }
      case other =>
        report.errorAndAbort(
          s"handler(...) requires a plain function literal, e.g. handler((id: Int) => ...); got: ${other.show}"
        )
    }
  }

  private def pathVarSymbol(using q: Quotes) =
    q.reflect.Symbol.requiredClass("zio.blocks.endpoint.PathVar")

  /** The sibling (NOT a subtype of [[pathVarSymbol]]) marker `zio.blocks.endpoint.PathVar.Ignored`
    * that `SegmentCodec`'s `.unused` builder relabels a leaf segment's `PathVars` entry to (see
    * zio-blocks' own `PathVar.scala` doc). Only ever appears on the PATTERN side (a `PV` decomposed
    * by [[decomposePathVarTuple]]) - a `handler(fn)`'s own declared parameter can never itself be
    * "marked unused", so [[tryDecomposePathVarTuple]] (which decomposes the HANDLER's `RequiredVars`
    * tuple) deliberately does NOT recognize this symbol.
    */
  private def pathVarIgnoredSymbol(using q: Quotes) =
    q.reflect.Symbol.requiredClass("zio.blocks.endpoint.PathVar.Ignored")

  /** Decomposes an ordered `PathVar[N0,T0] *: PathVar.Ignored[N1,T1] *: ... *: EmptyTuple` tuple
    * type (or `EmptyTuple`) into a `List[(name, type, isIgnored)]`, preserving order. `isIgnored`
    * is `true` for a `PathVar.Ignored[Name,Type]` element (a segment marked `.unused` in the route
    * pattern DSL, e.g. `string("b").unused`) and `false` for a plain `PathVar[Name,Type]` element.
    * An `Ignored` entry occupies a REAL slot in the resulting list, at the SAME position it has in
    * the pattern's real runtime value tuple - callers must never filter it out, since doing so
    * would silently shift every subsequent entry's index and corrupt positional runtime binding
    * (see `arrowImpl`'s `positions`/`isIdentity` logic, which indexes into this list's FULL length).
    */
  private def decomposePathVarTuple(using
    q: Quotes
  )(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr, Boolean)] = {
    import q.reflect._

    val pvSym         = pathVarSymbol
    val pvIgnoredSym   = pathVarIgnoredSymbol

    // `PV` is frequently a deeply-nested, un-reduced `PathVarTuples.Concat[L, R]` match-type
    // alias (from chained `/`/`~` composition, often over path-dependent operands like
    // `RoutePattern.GET.pathCodec.PathVars`) rather than an already-flattened tuple. A single
    // `.dealias`/`.simplified` pass does not fully reduce these - iterate to a fixpoint.
    def fullyReduce(t: TypeRepr): TypeRepr = {
      val next = t.dealias.simplified.dealias
      if (next =:= t) next else fullyReduce(next)
    }

    def literalName(nameTpe: TypeRepr): String =
      nameTpe.dealias match {
        case ConstantType(StringConstant(s)) => s
        case other                            =>
          report.errorAndAbort(s"Expected a literal string Name in PathVar, got: ${other.show}")
      }

    def loop(t: TypeRepr): List[(String, TypeRepr, Boolean)] = {
      val dt = fullyReduce(t)
      if (dt =:= TypeRepr.of[EmptyTuple]) Nil
      else
        dt match {
          case AppliedType(consTycon, List(head, tail)) if consTycon.typeSymbol.name == "*:" =>
            head.dealias match {
              case AppliedType(pvTycon, List(nameTpe, valTpe)) if pvTycon.typeSymbol == pvSym =>
                (literalName(nameTpe), valTpe, false) :: loop(tail)
              case AppliedType(pvTycon, List(nameTpe, valTpe)) if pvTycon.typeSymbol == pvIgnoredSym =>
                (literalName(nameTpe), valTpe, true) :: loop(tail)
              case other =>
                report.errorAndAbort(
                  s"Expected a PathVar[Name,Type] or PathVar.Ignored[Name,Type] tuple element, got: ${other.show}"
                )
            }
          case other =>
            report.errorAndAbort(s"Expected an ordered PathVar tuple, got: ${other.show}")
        }
    }

    loop(tpe)
  }

  def handlerImpl[H: Type](fnExpr: Expr[H])(using quotes: Quotes): Expr[Any] = {
    import quotes.reflect._

    val params = extractParams(fnExpr.asTerm)

    val requestTpe = TypeRepr.of[Request]
    val scopeTpe   = TypeRepr.of[Scope]

    // `-1` marks Request/Scope/Context params (resolved via `contextTypeOf`); a value `>= 0` is
    // this parameter's index among the path-var-eligible params, in declaration order.
    val contextTypeOf: List[Option[TypeRepr]] = params.map { case (_, tpe) =>
      if (tpe =:= requestTpe || tpe =:= scopeTpe || isPathVarEligible(tpe)) None else Some(tpe)
    }
    var pvCounter = 0
    val pathVarIndexOf: List[Int] = params.map { case (_, tpe) =>
      if (isPathVarEligible(tpe)) { val i = pvCounter; pvCounter += 1; i }
      else -1
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

    val pathVarEntries: List[(String, TypeRepr)] = params.filter { case (_, tpe) => isPathVarEligible(tpe) }

    def pathVarEntryType(name: String, tpe: TypeRepr): TypeRepr =
      TypeRepr.of[PathVar].appliedTo(List(ConstantType(StringConstant(name)), tpe))

    val reqVarsType: TypeRepr = pathVarEntries.foldRight(TypeRepr.of[EmptyTuple]) { case ((name, tpe), acc) =>
      TypeRepr.of[*:].appliedTo(List(pathVarEntryType(name, tpe), acc))
    }

    ctxType.asType match {
      case '[ctxT] =>
        reqVarsType.asType match {
          case '[reqVarsT] =>
            def buildArg(
              tpe: TypeRepr,
              maybeCtxTpe: Option[TypeRepr],
              pvIndex: Int,
              requestE: Expr[Request],
              contextE: Expr[Context[ctxT]],
              varsE: Expr[reqVarsT],
              scopeE: Expr[Scope]
            ): Term =
              if (tpe =:= requestTpe) requestE.asTerm
              else if (tpe =:= scopeTpe) scopeE.asTerm
              else
                maybeCtxTpe match {
                  case Some(t) =>
                    // ctxT is opaque here, so `t >: ctxT` (always true by construction: `t` is
                    // one of the conjuncts folded into ctxT above) isn't visible to the checker.
                    t.asType match {
                      case '[tt] => '{ $contextE.asInstanceOf[Context[tt]].get[tt] }.asTerm
                    }
                  case None    =>
                    // Unlike the route pattern's real value tuple `A` (which zio-blocks leaves
                    // unwrapped for a single capture), `reqVarsT` is ALWAYS a genuine tuple - a
                    // ONE-element `PathVar[N,T] *: EmptyTuple` for a single entry, never `T`
                    // directly (see PathVar.scala's "always a tuple" invariant) - so `vars` is
                    // ALWAYS accessed positionally here, regardless of arity.
                    tpe.asType match {
                      case '[tt] => '{ $varsE.asInstanceOf[Product].productElement(${ Expr(pvIndex) }).asInstanceOf[tt] }.asTerm
                    }
                }

            '{
              Handler.extracted[ctxT, reqVarsT]((request, context, vars, scope) =>
                ${
                  val argTerms = params.indices.toList.map { i =>
                    val (_, tpe) = params(i)
                    buildArg(tpe, contextTypeOf(i), pathVarIndexOf(i), 'request, 'context, 'vars, 'scope)
                  }
                  Apply(Select.unique(fnExpr.asTerm, "apply"), argTerms).asExprOf[Response | Halt]
                }
              )
            }
        }
    }
  }

  /** Like [[decomposePathVarTuple]], but returns `None` (via ordinary pattern matching, never
    * `report.errorAndAbort` - which aborts the whole compilation and is not something a
    * `try`/`catch` can intercept) instead of a compile error when `tpe` does not reduce to an
    * ordered `PathVar` tuple. Used to tell a `handler(fn)`-derived `Handler[Ctx, RequiredVars]`
    * (whose `Vars` is ALWAYS such a tuple, by construction in [[handlerImpl]]) apart from an
    * already-built `Handler[Ctx, V]` value (e.g. `Handler.succeed(...)`), whose `Vars` generally
    * is not.
    *
    * Deliberately does NOT recognize `PathVar.Ignored` (unlike [[decomposePathVarTuple]]): this
    * decomposes the HANDLER's own declared parameter tuple (built exclusively from plain
    * `PathVar[Name,Type]` entries by [[handlerImpl]]'s `pathVarEntryType`), never the route
    * pattern's `PathVars` registry - a handler parameter can never itself be "marked unused", only
    * a pattern-side `PathVars` entry can be.
    */
  private def tryDecomposePathVarTuple(using quotes: Quotes)(tpe: quotes.reflect.TypeRepr): Option[List[(String, quotes.reflect.TypeRepr)]] = {
    import quotes.reflect._

    def fullyReduce(t: TypeRepr): TypeRepr = {
      val next = t.dealias.simplified.dealias
      if (next =:= t) next else fullyReduce(next)
    }

    def loop(t: TypeRepr): Option[List[(String, TypeRepr)]] = {
      val dt = fullyReduce(t)
      if (dt =:= TypeRepr.of[EmptyTuple]) Some(Nil)
      else
        dt match {
          case AppliedType(consTycon, List(head, tail)) if consTycon.typeSymbol.name == "*:" =>
            head.dealias match {
              case AppliedType(pvTycon, List(nameTpe, valTpe)) if pvTycon.typeSymbol == pathVarSymbol =>
                nameTpe.dealias match {
                  case ConstantType(StringConstant(s)) => loop(tail).map((s, valTpe) :: _)
                  case _                                => None
                }
              case _ => None
            }
          case _ => None
        }
    }

    loop(tpe)
  }

  /**
   * Note on why this inspects `h`'s TYPE rather than its syntax tree: an earlier version tried to
   * detect "is `h` syntactically a call to `RouteBinding.handler`" from the raw AST. That does not
   * work: since [[RouteBinding.handler]] is itself `transparent inline`, Scala fully expands it
   * (to determine its precise type) as an ordinary part of elaborating `->`'s `inline h: Any`
   * argument, BEFORE this macro body ever runs - by the time `arrowImpl` inspects `h`, its syntax
   * tree is already the fully-expanded `Handler.extracted[Ctx, RequiredVars](...)` call, not the
   * original `handler(fn)` call. Inspecting `h`'s resulting TYPE instead is exactly as precise
   * (`RequiredVars` is uniquely and only ever a `PathVar` tuple when produced by [[handlerImpl]])
   * and is robust to however the expansion happens to be represented.
   */
  def arrowImpl[A: Type, PV: Type](
    patternExpr: Expr[RoutePattern[A] { type PathVars = PV }],
    hExpr: Expr[Any]
  )(using quotes: Quotes): Expr[Route[Nothing]] = {
    import quotes.reflect._

    val pvEntries  = decomposePathVarTuple(TypeRepr.of[PV])
    val hType      = hExpr.asTerm.tpe.widen
    val handlerSym = Symbol.requiredClass("zio.http.Handler")

    hType.baseType(handlerSym) match {
      case AppliedType(_, List(ctxArg, varsArg)) =>
        tryDecomposePathVarTuple(varsArg) match {
          case Some(reqEntries) =>
            var consumed = Set.empty[Int]
            // Matching cares only about (name, type) equality, never about `isIgnored` - a
            // `.unused` pattern segment (D-Ignored's whole point) remains perfectly bindable if a
            // handler parameter actually references it; `isIgnored` only affects which WARNING (if
            // any) is emitted below, never whether a match succeeds.
            val positions: List[Int] = reqEntries.map { case (name, tpe) =>
              pvEntries.zipWithIndex.find { case ((pvName, pvType, _), idx) =>
                pvName == name && pvType =:= tpe && !consumed.contains(idx)
              } match {
                case Some((_, idx)) =>
                  consumed += idx
                  idx
                case None            =>
                  report.errorAndAbort(
                    s"PathVar `$name: ${tpe.show}` is required by this handler but is not provided by the " +
                      s"route pattern (which declares: ${pvEntries.map { case (n, t, _) => s"$n: ${t.show}" }.mkString(", ")})."
                  )
                  -1
              }
            }

            // Each warned-about entry must be reported at a DISTINCT `Position`: dotc's default
            // reporter (`UniqueMessagePositions`) silently drops any diagnostic whose position was
            // already seen, regardless of message text - calling `report.warning(msg)` (no explicit
            // position) for every entry reports them all at the SAME default macro-expansion
            // position, so only the FIRST of several unused-var warnings ever surfaced (confirmed via
            // a real `-Werror` compile: 2 unused vars produced only 1 warning, not 2 - a real Todo 6
            // finding, not a hypothetical). Fix: nudge each entry's position by its index within the
            // macro-expansion range (clamped to stay inside that range) so every entry gets a position
            // the reporter has not already recorded. Since every `pvEntries` index is unique and each
            // entry emits AT MOST one of the two warnings below (an entry is either consumed or not,
            // never both), this single index-keyed offset scheme keeps BOTH warning kinds mutually
            // distinct too, with no extra bookkeeping needed.
            //
            // Four `(isIgnored, consumed)` combinations, exactly per the `.unused` design:
            //   - plain, unconsumed  -> existing "defined in the path but is never used" warning.
            //   - plain, consumed    -> no warning (unchanged existing behavior).
            //   - ignored, unconsumed-> no warning (the whole point of `.unused`: suppress it).
            //   - ignored, consumed  -> NEW "marked .unused but is referenced" warning (a lint, not
            //                           an error - the segment still binds correctly either way).
            val macroPos = Position.ofMacroExpansion
            pvEntries.zipWithIndex.foreach { case ((name, tpe, isIgnored), idx) =>
              val offset      = math.min(macroPos.start + idx, macroPos.end)
              val distinctPos = Position(macroPos.sourceFile, offset, offset)
              val displayType = tpe.show.split('.').last
              if (!isIgnored && !consumed.contains(idx))
                report.warning(s"Variable $name:$displayType was defined in the path but is never used", distinctPos)
              else if (isIgnored && consumed.contains(idx))
                report.warning(s"Variable $name:$displayType was marked .unused but is referenced by the handler", distinctPos)
            }

            val n = pvEntries.length

            // Todo 8 (D8 allocation-neutrality) finding: when `positions` is the IDENTITY
            // permutation over ALL `n` pattern vars (handler consumes every var, in the pattern's
            // own declared order - e.g. Worked Example 2's `GET / int("userId") / string("postId")
            // -> handler((userId: Int, postId: String) => ...)`), `a` (the pattern's real runtime
            // value tuple) is ALREADY the exact `Product` shape `varsT` needs - rebuilding a fresh
            // tuple via `*:` (`buildTuple` below) is pure waste: a real `javap`+JMH-`-prof gc`
            // regression (confirmed: 2 extra `scala.runtime.Tuples$.cons` allocations per call,
            // +56 B/op vs. a hand-written baseline that accesses `a`'s fields directly) with zero
            // behavioral benefit, since reusing `a` (relabeled to the phantom `varsT` via a
            // zero-cost `asInstanceOf`) is bit-for-bit identical to what the rebuilt tuple would
            // have contained. For any NON-identity case (partial use and/or reordered use), a real
            // reshape is unavoidable - the pattern's positions must actually be reordered/dropped
            // into the handler's own declared order, and rebuilding is kept exactly as before.
            val isIdentity = n >= 2 && positions == List.range(0, n)

            def buildTuple(elems: List[Term]): Term = elems match {
              case Nil          => '{ EmptyTuple }.asTerm
              case head :: tail =>
                '{ ${ head.asExprOf[Any] } *: ${ buildTuple(tail).asExprOf[Tuple] } }.asTerm
            }

            ctxArg.asType match {
              case '[ctxT] =>
                varsArg.asType match {
                  case '[varsT] =>
                    val hE: Expr[Handler[ctxT, varsT]] = hExpr.asExprOf[Handler[ctxT, varsT]]
                    val built: Expr[Route[ctxT]] = '{
                      Route(
                        $patternExpr,
                        Handler.extracted[ctxT, A]((request, context, a, scope) =>
                          ${
                            val varsTuple: Expr[varsT] =
                              if (isIdentity) '{ a.asInstanceOf[varsT] }
                              else {
                                val elemTerms: List[Term] = positions.map { pos =>
                                  if (n <= 1) '{ a }.asTerm
                                  else '{ a.asInstanceOf[Product].productElement(${ Expr(pos) }) }.asTerm
                                }
                                val varsTupleAny = buildTuple(elemTerms).asExprOf[Any]
                                '{ $varsTupleAny.asInstanceOf[varsT] }
                              }
                            '{ $hE.handle(request, context, $varsTuple, scope) }
                          }
                        )
                      )
                    }
                    built
                }
            }

          case None =>
            ctxArg.asType match {
              case '[ctxT] =>
                val built: Expr[Route[ctxT]] = '{ Route($patternExpr, $hExpr.asInstanceOf[Handler[ctxT, A]]) }
                built
            }
        }
      case _ =>
        report.errorAndAbort(
          "`->` expects either a `handler(...)`-wrapped function or an existing `Handler[Ctx, V]` " +
            s"value (e.g. `Handler.succeed(...)`); got a value of type: ${hType.show}"
        )
    }
  }
}
