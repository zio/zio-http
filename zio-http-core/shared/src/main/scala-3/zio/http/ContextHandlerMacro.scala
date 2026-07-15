package zio.http

import scala.quoted.*
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope

private[http] object ContextHandlerMacro {

  /**
   * Generates a `Handler` from a function literal with trailing context
   * parameters.
   *
   * Scala 3 supports arbitrary function arity (including FunctionXXL > 22).
   * Each context type `T` must have an `IsNominalType[T]` instance available.
   * No artificial arity cap exists.
   */
  def impl[H: Type](h: Expr[H])(using q: Quotes): Expr[Handler[?, ?]] = {
    import q.reflect.*

    val T = TypeRepr.of[H].dealias
    T match {
      case AppliedType(fn, args) if fn.typeSymbol.fullName.startsWith("scala.Function") && args.size >= 2 =>
        val params   = args.init
        val ret      = args.last
        if (!(params.head <:< TypeRepr.of[Request]))
          report.errorAndAbort(s"contextHandler: first param must be Request, got ${params.head.show}")
        if (!(ret <:< TypeRepr.of[Response | Halt]))
          report.errorAndAbort(s"contextHandler: return type must be Response | Halt, got ${ret.show}")
        val ctxTypes = params.tail
        if (ctxTypes.isEmpty)
          report.errorAndAbort("contextHandler: expected context types after Request")
        ctxTypes.foreach { t =>
          Implicits.search(TypeRepr.of[IsNominalType].appliedTo(List(t))) match {
            case _: ImplicitSearchSuccess => ()
            case _                        => report.errorAndAbort(s"Context type ${t.show} is not nominal.")
          }
        }
        genHandler(h, ctxTypes.map(t => t: Any))
      case _                                                                                              =>
        report.errorAndAbort(s"contextHandler: expected (Request, Ctx, ...) => Response | Halt, got ${T.show}")
    }
  }

  private def findIsNominal(using q: Quotes)(t: q.reflect.TypeRepr): q.reflect.Term = {
    import q.reflect.*
    Implicits.search(TypeRepr.of[IsNominalType].appliedTo(List(t))) match {
      case s: ImplicitSearchSuccess => s.tree
      case _                        => report.errorAndAbort(s"Cannot find IsNominalType for ${t.show}.")
    }
  }

  private def genHandler[H: Type](h: Expr[H], ctxTypesRaw: List[Any])(using q: Quotes): Expr[Handler[?, ?]] = {
    import q.reflect.*
    val ctxTypes = ctxTypesRaw.asInstanceOf[List[q.reflect.TypeRepr]]
    val combined = ctxTypes.reduce(AndType(_, _))

    // combined is a TypeRepr; we need to convert to Type[c] for the quote
    combined.asType match {
      case t @ '[c] =>
        val fnTerm = h.asTerm
        '{
          Handler.extracted[c, Any] { (req: Request, ctx: Context[c], vars: Any, scope: Scope) =>
            ${
              val body = genBody(using q)(fnTerm, ctxTypes, '{ req }, '{ ctx })
              body
            }
          }
        }.asExprOf[Handler[?, ?]]
    }
  }

  private def genBody(using
    q: Quotes,
  )(
    fnTerm: q.reflect.Term,
    ctxTypesRaw: List[Any],
    req: Expr[Request],
    ctx: Expr[Context[?]],
  ): Expr[Response | Halt] = {
    import q.reflect.*
    val ctxTypes = ctxTypesRaw.asInstanceOf[List[q.reflect.TypeRepr]]

    def loop(rem: List[q.reflect.TypeRepr], acc: List[Expr[Any]]): Expr[Response | Halt] = rem match {
      case Nil       =>
        val args: List[Term] = (req.asTerm :: acc.map(_.asTerm)).asInstanceOf[List[Term]]
        val fnTpe            = fnTerm.tpe.widen
        val callTerm: Term   =
          if (fnTpe.typeSymbol.fullName == "scala.FunctionXXL") {
            val iarrayTpe  = TypeRepr.of[IArray[Any]]
            val consSym    = iarrayTpe.typeSymbol.companionModule.methodMember("apply").head
            val consSelect = Select(Ref(iarrayTpe.typeSymbol.companionModule), consSym)
            val iarrayCall = Apply(consSelect, args)
            val applyXXL   = fnTpe.typeSymbol.methodMember("apply").head
            Apply(Select(fnTerm, applyXXL), List(iarrayCall))
          } else {
            val applySym = fnTpe.typeSymbol.methodMember("apply").head
            Apply(Select(fnTerm, applySym), args)
          }
        callTerm.asExprOf[Response | Halt]
      case t :: rest =>
        t.asType match {
          case '[tpe] =>
            val evTerm                           = findIsNominal(using q)(t)
            val evExpr: Expr[IsNominalType[tpe]] = evTerm.asExprOf[IsNominalType[tpe]]
            val g: Expr[tpe]                     = '{ $ctx.asInstanceOf[Context[tpe]].get[tpe](using $evExpr) }
            loop(rest, acc :+ g.asExprOf[Any])
        }
    }
    loop(ctxTypes, Nil)
  }
}
