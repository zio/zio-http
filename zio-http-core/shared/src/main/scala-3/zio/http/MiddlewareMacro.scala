package zio.http

import scala.quoted.*
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope

private[http] object MiddlewareMacro {

  /**
   * Generates a `Middleware` from a function literal.
   *
   * Scala 3 supports arbitrary function arity (including FunctionXXL > 22).
   * Each context type `T` must have an `IsNominalType[T]` instance available.
   */
  def customImpl[F: Type](f: Expr[F])(using q: Quotes): Expr[Middleware[?, ?]] = {
    import q.reflect.*

    val funType               = TypeRepr.of[F].dealias
    val (paramTypes, retType) = funType match {
      case AppliedType(tc, args) if tc.typeSymbol.fullName.startsWith("scala.Function") =>
        (args.init, args.last)
      case _ => report.errorAndAbort(s"Middleware.custom expects a function type, got ${funType.show}.")
    }

    if (paramTypes.size < 2)
      report.errorAndAbort(s"Middleware.custom: needs at least (Request, Scope). Got ${paramTypes.size}.")
    if (!(paramTypes(0) <:< TypeRepr.of[Request]))
      report.errorAndAbort(s"First param must be Request, got ${paramTypes(0).show}.")
    if (!(paramTypes(1) <:< TypeRepr.of[Scope]))
      report.errorAndAbort(s"Second param must be Scope, got ${paramTypes(1).show}.")

    val inTypes  = paramTypes.drop(2)
    val outTypes = extractOutTypes(retType)
    val fnTerm   = f.asTerm

    '{
      new Middleware[Any, Any] {
        def apply(routes: Routes[Any]): Routes[Any] = {
          val _fn: Any = ${ fnTerm.asExprOf[Any] }
          Routes.fromIterable(routes.routes.toList.map { (route: Route[Any]) =>
            val _next    = route.handler
            val _wrapped = Handler.extracted[Any, Any] { (req: Request, ctx: Context[Any], vars: Any, scope: Scope) =>
              ${ genBody(using q)(inTypes, outTypes, fnTerm, '_next, 'req, 'ctx, 'vars, 'scope) }
            }
            Route(route.pattern, _wrapped)
          })
        }
      }
    }.asExprOf[Middleware[?, ?]]
  }

  private def extractOutTypes(using q: Quotes)(t: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect.*
    t.dealias match {
      case OrType(a, b) => extractOutTypes(a) ++ extractOutTypes(b)
      case AppliedType(tc, as)
          if tc.typeSymbol.fullName.startsWith("scala.Tuple") && as.nonEmpty
            && (as.head <:< TypeRepr.of[Response]) =>
        as.tail
      case _            => Nil
    }
  }

  private def findIsNominal(using q: Quotes)(t: q.reflect.TypeRepr): q.reflect.Term = {
    import q.reflect.*
    Implicits.search(TypeRepr.of[IsNominalType].appliedTo(List(t))) match {
      case s: ImplicitSearchSuccess => s.tree
      case _                        => report.errorAndAbort(s"Cannot find IsNominalType for ${t.show}.")
    }
  }

  private def genBody(using
    q: Quotes,
  )(
    inTypes: List[q.reflect.TypeRepr],
    outTypes: List[q.reflect.TypeRepr],
    fnTerm: q.reflect.Term,
    nextExpr: Expr[Handler[Any, Any]],
    reqExpr: Expr[Request],
    ctxExpr: Expr[Context[Any]],
    varsExpr: Expr[Any],
    scopeExpr: Expr[Scope],
  ): Expr[Response | Halt] = {
    import q.reflect.*

    def loop(rem: List[q.reflect.TypeRepr], acc: List[Expr[Any]]): Expr[Response | Halt] = rem match {
      case Nil       =>
        buildResult(using q)(fnTerm, acc, outTypes, nextExpr, reqExpr, ctxExpr, varsExpr, scopeExpr)
      case t :: rest =>
        t.asType match {
          case '[tpe] =>
            val evTerm                           = findIsNominal(using q)(t)
            val evExpr: Expr[IsNominalType[tpe]] = evTerm.asExprOf[IsNominalType[tpe]]
            val g: Expr[tpe]                     = '{
              $ctxExpr.asInstanceOf[Context[tpe]].get[tpe](using $evExpr)
            }
            loop(rest, acc :+ g.asExprOf[Any])
        }
    }
    loop(inTypes, Nil)
  }

  private def buildResult(using
    q: Quotes,
  )(
    fnTerm: q.reflect.Term,
    getters: List[Expr[Any]],
    outTypes: List[q.reflect.TypeRepr],
    nextExpr: Expr[Handler[Any, Any]],
    reqExpr: Expr[Request],
    ctxExpr: Expr[Context[Any]],
    varsExpr: Expr[Any],
    scopeExpr: Expr[Scope],
  ): Expr[Response | Halt] = {
    import q.reflect.*

    val args: List[Term]    = (reqExpr.asTerm :: scopeExpr.asTerm :: getters.map(_.asTerm)).asInstanceOf[List[Term]]
    val fnTpe               = fnTerm.tpe.widen
    val callTerm: Term      =
      if (fnTpe.typeSymbol.fullName == "scala.FunctionXXL") {
        val iarrayTpe  = TypeRepr.of[IArray[Any]]
        val consSym    = iarrayTpe.typeSymbol.companionModule.methodMember("apply").head
        val consSelect = Select(Ref(iarrayTpe.typeSymbol.companionModule), consSym)
        val iarrayCall = args.foldLeft[Term](consSelect) { (acc, a) => Apply(acc, List(a)) }
        val applyXXL   = fnTpe.typeSymbol.methodMember("apply").head
        Apply(Select(fnTerm, applyXXL), List(iarrayCall))
      } else {
        val applySym = fnTpe.typeSymbol.methodMember("apply").head
        Apply(Select(fnTerm, applySym), args)
      }
    val callExpr: Expr[Any] = callTerm.asExprOf[Any]

    if (outTypes.isEmpty) {
      '{ ${ callExpr }.asInstanceOf[Response | Halt] }
    } else {
      val enrichFn: Expr[Any => Response | Halt] =
        buildEnrichTerm(using q)(
          outTypes,
          nextExpr.asTerm,
          reqExpr.asTerm,
          ctxExpr.asTerm,
          varsExpr.asTerm,
          scopeExpr.asTerm,
        )
      '{
        val __r: Any = ${ callExpr }
        __r match {
          case r: Response => r
          case h: Halt     => h
          case _           => ${ enrichFn }(__r)
        }
      }
    }
  }

  /**
   * Build enrichment function via Term-level Lambda (avoids staging issues).
   */
  private def buildEnrichTerm(using
    q: Quotes,
  )(
    outTypes: List[q.reflect.TypeRepr],
    nextTerm: q.reflect.Term,
    reqTerm: q.reflect.Term,
    ctxTerm: q.reflect.Term,
    varsTerm: q.reflect.Term,
    scopeTerm: q.reflect.Term,
  ): Expr[Any => Response | Halt] = {
    import q.reflect.*

    val asI = TypeRepr.of[Any].typeSymbol.methodMember("asInstanceOf").head
    val aS  = TypeRepr.of[Context[Any]].typeSymbol.methodMember("add").head
    val pE  = TypeRepr.of[scala.Product].typeSymbol.methodMember("productElement").head
    val hS  = TypeRepr.of[Handler[Any, Any]].typeSymbol.methodMember("handle").head

    val mt = MethodType(List("r"))(_ => List(TypeRepr.of[Any]), _ => TypeRepr.of[Response | Halt])
    val l  = Lambda(
      Symbol.spliceOwner,
      mt,
      (meth, lst) => {
        val resultT = lst.head.asInstanceOf[Term]
        val pS = Symbol.newVal(Symbol.spliceOwner, "_p", TypeRepr.of[scala.Product], Flags.EmptyFlags, Symbol.noSymbol)
        val pV = ValDef(pS, Some(TypeApply(Select(resultT, asI), List(Inferred(TypeRepr.of[scala.Product])))))
        val pR = Ref(pS).asInstanceOf[Term]
        val oS = outTypes.indices.toList.map(i =>
          Symbol.newVal(Symbol.spliceOwner, "_o" + i, outTypes(i), Flags.EmptyFlags, Symbol.noSymbol),
        )
        val oV = oS.zipWithIndex.map { case (s, i) =>
          val el = Select(pR, pE).appliedToArgs(List(Literal(IntConstant(i + 1))))
          ValDef(s, Some(TypeApply(Select(el, asI), List(Inferred(outTypes(i))))))
        }
        var prevCtx = ctxTerm
        val aStmts = oS.zipWithIndex.map { case (s, i) =>
          val eS   =
            Symbol.newVal(Symbol.spliceOwner, "_e" + i, TypeRepr.of[Context[Any]], Flags.EmptyFlags, Symbol.noSymbol)
          val addR = Apply(TypeApply(Select(prevCtx, aS), List(Inferred(outTypes(i)))), List(Ref(s).asInstanceOf[Term]))
          prevCtx = Ref(eS).asInstanceOf[Term]
          ValDef(eS, Some(addR))
        }
        val hCall  = Select(TypeApply(Select(nextTerm, asI), List(Inferred(TypeRepr.of[Handler[Any, Any]]))), hS)
          .appliedToArgs(
            List(
              reqTerm,
              TypeApply(Select(prevCtx, asI), List(Inferred(TypeRepr.of[Context[Any]]))),
              varsTerm,
              scopeTerm,
            ),
          )
        Block(pV :: oV ++ aStmts, hCall)
      },
    )
    l.asExprOf[Any => Response | Halt]
  }
}
