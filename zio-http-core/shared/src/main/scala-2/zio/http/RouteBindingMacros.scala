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

    // Parses a PathVarTuples-encoded `PathVars` type (`Unit` | `PathVar[N,T]` TupleN, per
    // zio-blocks' Scala 2.13 encoding) into an ordered `(name, type)` list - shared parsing logic
    // for BOTH `PV` (the route pattern's registry) and `Req` (phase 1's open requirements), since
    // both use the IDENTICAL encoding.
    def parseEntries(whole: Type): List[(String, Type)] = {
      val w = whole.dealias
      if (w =:= typeOf[Unit]) Nil
      else {
        val elems = if (isTupleType(w)) w.typeArgs else List(w)
        elems.map { e =>
          val args = e.dealias.typeArgs
          val name = args.head match {
            case ConstantType(Constant(s: String)) => s
            case other                              =>
              c.abort(c.enclosingPosition, s"Expected a literal String singleton type for a PathVar Name, but found: $other")
          }
          (name, args(1).dealias)
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

    val accessExprs: List[Tree] = reqEntries.map { case (rName, rType) =>
      val foundIdx = pvEntries.indices.find(i => !consumed(i) && pvEntries(i)._1 == rName && pvEntries(i)._2 =:= rType)
      foundIdx match {
        case Some(idx) =>
          consumed(idx) = true
          accessAt(idx)
        case None      =>
          c.abort(
            c.enclosingPosition,
            s"No path variable named `$rName` of type $rType is declared by this route pattern " +
              s"(or it was already consumed by an earlier handler parameter of the same name and type)."
          )
      }
    }

    pvEntries.zipWithIndex.foreach { case ((name, tpe), idx) =>
      if (!consumed(idx))
        c.warning(c.enclosingPosition, s"Variable $name:$tpe was defined in the path but is never used")
    }

    val reqRuntimeShape: Tree = reqEntries.length match {
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
