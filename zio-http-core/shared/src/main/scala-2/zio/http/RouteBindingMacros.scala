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

import scala.reflect.macros.blackbox

/**
 * Blackbox macro implementation backing [[RouteBinding.RoutePatternArrowOps]]'s macro-derived
 * `->` overload - BLACKBOX (unlike [[PathVarHandlerMacros]]) because its return type, `Route[Ctx]`,
 * is already the exact, final declared type: no return-type refinement is needed, only AST
 * inspection of two already-fully-known phantom Tuple types (`PV`, `Req`) plus `c.warning`/
 * `c.abort` diagnostics.
 */
private[http] object RouteBindingMacros {

  def arrowImpl[A: c.WeakTypeTag, PV: c.WeakTypeTag, Ctx: c.WeakTypeTag, Req: c.WeakTypeTag](
    c: blackbox.Context
  )(h: c.Expr[Handler[Ctx, Req]]): c.Tree = {
    import c.universe._

    def isTupleType(tpe: Type): Boolean = {
      val sym = tpe.typeSymbol
      sym.fullName.startsWith("scala.Tuple") && sym.fullName.matches("scala\\.Tuple[0-9]+")
    }

    // The sibling (NOT a subtype of `PathVar`) marker `zio.blocks.endpoint.PathVar.Ignored` that
    // `SegmentCodec`'s `.unused` builder relabels a leaf segment's `PathVars` entry to (see
    // zio-blocks' own `PathVar.scala` doc, and the Scala 3 sibling implementation in
    // `RouteBinding.scala`'s `pathVarIgnoredSymbol`). Only ever appears on the PATTERN side (`PV`,
    // parsed via `parseEntries(pvType)` below) - a `handler(fn)`'s own declared parameter can never
    // itself be "marked unused", so `parseEntries(reqType)` never encounters it in practice, even
    // though the same helper is used for both (see its own doc comment).
    val pathVarIgnoredSym = typeOf[zio.blocks.endpoint.PathVar.Ignored[_, _]].typeSymbol

    // Parses a PathVarTuples-encoded `PathVars` type (`Unit` | `PathVar[N,T]`/`PathVar.Ignored[N,T]`
    // TupleN, per zio-blocks' Scala 2.13 encoding) into an ordered `(name, type, isIgnored)` list -
    // shared parsing logic for BOTH `PV` (the route pattern's registry) and `Req` (phase 1's open
    // requirements), since both use the IDENTICAL encoding. `isIgnored` is `true` for a
    // `PathVar.Ignored[Name,Type]` element (a segment marked `.unused` in the route pattern DSL,
    // e.g. `SegmentCodec.string("b").unused`) and `false` for a plain `PathVar[Name,Type]` element -
    // `Req` entries are always `false` in practice (see above), so this flag is meaningful only for
    // `pvEntries`. An `Ignored` entry occupies a REAL slot in the resulting list, at the SAME
    // position it has in the pattern's real runtime value tuple - callers must never filter it out,
    // since doing so would silently shift every subsequent entry's index and corrupt positional
    // runtime binding (see `positions`/`isIdentity` below, which index into this list's FULL length).
    def parseEntries(whole: Type): List[(String, Type, Boolean)] = {
      val w = whole.dealias
      if (w =:= typeOf[Unit]) Nil
      else {
        val elems = if (isTupleType(w)) w.typeArgs else List(w)
        elems.map { e =>
          val dealiased = e.dealias
          val args      = dealiased.typeArgs
          val name      = args.head match {
            case ConstantType(Constant(s: String)) => s
            case other                              =>
              c.abort(c.enclosingPosition, s"Expected a literal String singleton type for a PathVar Name, but found: $other")
          }
          val isIgnored = dealiased.typeSymbol == pathVarIgnoredSym
          (name, args(1).dealias, isIgnored)
        }
      }
    }

    val aType   = weakTypeOf[A].dealias
    val pvType  = weakTypeOf[PV].dealias
    val ctxType = weakTypeOf[Ctx].dealias
    val reqType = weakTypeOf[Req].dealias

    val pvEntries  = parseEntries(pvType)
    val reqEntries = parseEntries(reqType)
    val pvArity    = pvEntries.length

    val consumed = Array.fill(pvEntries.length)(false)

    val aVarsName = TermName(c.freshName("aVars"))

    def accessAt(pvIndex: Int): Tree =
      if (pvArity == 1) q"$aVarsName.asInstanceOf[$aType]"
      else q"$aVarsName.asInstanceOf[$aType].${TermName(s"_${pvIndex + 1}")}"

    val positions                = scala.collection.mutable.ListBuffer.empty[Int]
    val accessExprs: List[Tree] = reqEntries.map { case (rName, rType, _) =>
      // Matching cares only about (name, type) equality, never about `isIgnored` - a `.unused`
      // pattern segment (the whole point of `PathVar.Ignored`) remains perfectly bindable if a
      // handler parameter actually references it; `isIgnored` only affects which warning (if any)
      // is emitted below, never whether a match succeeds.
      val foundIdx = pvEntries.indices.find(i => !consumed(i) && pvEntries(i)._1 == rName && pvEntries(i)._2 =:= rType)
      foundIdx match {
        case Some(idx) =>
          consumed(idx) = true
          positions += idx
          accessAt(idx)
        case None      =>
          c.abort(
            c.enclosingPosition,
            s"No path variable named `$rName` of type $rType is declared by this route pattern " +
              s"(or it was already consumed by an earlier handler parameter of the same name and type)."
          )
      }
    }

    // Four `(isIgnored, consumed)` combinations, exactly per the `.unused` design (see
    // `PathVar.Ignored`'s doc, and the identical Scala 3 sibling logic in `RouteBinding.scala`):
    //   - plain, unconsumed   -> existing "defined in the path but is never used" warning.
    //   - plain, consumed     -> no warning (unchanged existing behavior).
    //   - ignored, unconsumed -> no warning (the whole point of `.unused`: suppress it).
    //   - ignored, consumed   -> NEW "marked .unused but is referenced" warning (a lint, not an
    //                            error - the segment still binds correctly either way, see
    //                            `accessExprs` above which does not special-case `isIgnored`).
    // `c.warning(pos, msg)` at the SAME `c.enclosingPosition` for multiple distinct entries is
    // safe here, unlike dotc's `report.warning` on the Scala 3 sibling: Scala 2.13's blackbox
    // macro reporter has no `UniqueMessagePositions`-style same-position dedup (confirmed
    // empirically by the pre-existing test 14 below, which already asserts TWO distinct warnings
    // fire from two entries sharing the same `c.enclosingPosition`) - so no per-entry position
    // offset is needed here, unlike the Scala 3 macro.
    pvEntries.zipWithIndex.foreach { case ((name, tpe, isIgnored), idx) =>
      if (!isIgnored && !consumed(idx))
        c.warning(c.enclosingPosition, s"Variable $name:$tpe was defined in the path but is never used")
      else if (isIgnored && consumed(idx))
        c.warning(c.enclosingPosition, s"Variable $name:$tpe was marked .unused but is referenced by the handler")
    }

    // Todo 8 (D8 allocation-neutrality) finding, parity with the Scala 3 fix in
    // `RouteBinding.scala`: when `positions` is the IDENTITY permutation over ALL `pvArity`
    // pattern vars (handler consumes every var, in the pattern's own declared order),
    // `$aVarsName` is ALREADY the exact runtime shape `reqRuntimeShapeType` needs - building a
    // fresh `(..$accessExprs)` tuple literal (which compiles to a real `new TupleN(...)`) is pure
    // waste, confirmed via `javap` (a genuine extra `new scala/Tuple2` per call, absent from a
    // hand-written baseline) and JMH `-prof gc`. Reusing `$aVarsName` via a zero-cost
    // `asInstanceOf` is bit-for-bit identical. Any non-identity case (partial and/or reordered
    // use) still needs a real reshape and is unaffected.
    val isIdentity = pvArity >= 2 && positions.toList == List.range(0, pvArity)

    val reqRuntimeShape: Tree =
      if (isIdentity) q"$aVarsName.asInstanceOf[$aType]"
      else
        reqEntries.length match {
          case 0 => q"()"
          case 1 => accessExprs.head
          case _ => q"(..$accessExprs)"
        }

    // The REAL runtime shape backing `Req` (phase 1's phantom `RequiredVars`) - Unit for zero
    // open params, the bare type for exactly one, a real TupleN for two or more. Mirrors
    // `PathVarHandlerMacros.runtimeShapeOf`; must match it exactly since phase 1 is the one
    // producing the value that flows through here.
    val reqRuntimeShapeType: Type = reqEntries.map(_._2) match {
      case Nil           => typeOf[Unit]
      case single :: Nil => single
      case many          => appliedType(definitions.TupleClass(many.length), many)
    }

    val requestName = TermName(c.freshName("request"))
    val contextName = TermName(c.freshName("context"))
    val scopeName   = TermName(c.freshName("scope"))

    // `h`'s underlying `Extracted` instance (built by phase 1) actually expects
    // `reqRuntimeShapeType`, not the phantom `Req` (`$reqType`) it is statically typed with -
    // reparameterizing `h` itself via `.asInstanceOf` (a zero-cost generic-trait
    // reparameterization) lets `reqRuntimeShape` be passed as a plain, correctly-shaped value
    // with NO value-level cast to a concrete, non-erased type (which would throw a real
    // `ClassCastException` for the same reason documented in `PathVarHandlerMacros`).
    val newHandler =
      q"""
        _root_.zio.http.Handler.extracted[$ctxType, $aType](
          (
            $requestName: _root_.zio.http.Request,
            $contextName: _root_.zio.blocks.context.Context[$ctxType],
            $aVarsName: $aType,
            $scopeName: _root_.zio.blocks.scope.Scope
          ) => ${h.tree}
                 .asInstanceOf[_root_.zio.http.Handler[$ctxType, $reqRuntimeShapeType]]
                 .handle($requestName, $contextName, $reqRuntimeShape, $scopeName)
        )
      """

    q"""
      _root_.zio.http.Route.apply[$ctxType, $aType](
        ${c.prefix.tree}.self.asInstanceOf[_root_.zio.blocks.endpoint.RoutePattern[$aType]],
        $newHandler
      )
    """
  }
}
