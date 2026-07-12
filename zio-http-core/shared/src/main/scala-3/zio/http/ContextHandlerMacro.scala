package zio.http

import scala.quoted.*
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope

private[http] object ContextHandlerMacro {

  def impl[H: Type](h: Expr[H])(using q: Quotes): Expr[Handler[?, ?]] = {
    import q.reflect.*

    val T = TypeRepr.of[H].dealias
    T match {
      case AppliedType(fn, args) if fn.typeSymbol.fullName.startsWith("scala.Function") && args.size >= 3 =>
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

  private def genHandler[H: Type](h: Expr[H], ctxTypesRaw: List[Any])(using q: Quotes): Expr[Handler[?, ?]] = {
    import q.reflect.*
    val ctxTypes = ctxTypesRaw.asInstanceOf[List[q.reflect.TypeRepr]]
    val combined = ctxTypes.reduce(AndType(_, _))

    // combined is a TypeRepr; we need to convert to Type[c] for the quote
    combined.asType match {
      case t @ '[c] =>
        val fn = h.asExprOf[Any]
        '{
          Handler.extracted[c, Any] { (req: Request, ctx: Context[c], vars: Any, scope: Scope) =>
            ${
              val body = genBody(fn, ctxTypes, '{ req }, '{ ctx })
              body
            }
          }
        }.asExprOf[Handler[?, ?]]
    }
  }

  private def genBody(
    fn: Expr[Any],
    ctxTypesRaw: List[Any],
    req: Expr[Request],
    ctx: Expr[Context[?]],
  )(using q: Quotes): Expr[Response | Halt] = {
    import q.reflect.*
    val ctxTypes = ctxTypesRaw.asInstanceOf[List[q.reflect.TypeRepr]]

    def loop(rem: List[q.reflect.TypeRepr], acc: List[Expr[Any]]): Expr[Response | Halt] = rem match {
      case Nil       =>
        val n    = acc.size + 1
        val call = n match {
          case 2 => '{ $fn.asInstanceOf[Function2[Request, Any, Any]].apply($req, ${ acc(0) }) }
          case 3 => '{ $fn.asInstanceOf[Function3[Request, Any, Any, Any]].apply($req, ${ acc(0) }, ${ acc(1) }) }
          case 4 =>
            '{
              $fn
                .asInstanceOf[Function4[Request, Any, Any, Any, Any]]
                .apply($req, ${ acc(0) }, ${ acc(1) }, ${ acc(2) })
            }
          case 5 =>
            '{
              $fn
                .asInstanceOf[Function5[Request, Any, Any, Any, Any, Any]]
                .apply($req, ${ acc(0) }, ${ acc(1) }, ${ acc(2) }, ${ acc(3) })
            }
          case _ => '{ $fn.asInstanceOf[Function2[Request, Any, Any]].apply($req, ${ acc(0) }) }
        }
        '{ ${ call }.asInstanceOf[Response | Halt] }
      case t :: rest =>
        t.asType match {
          case '[tpe] =>
            val g: Expr[tpe] = '{ $ctx.asInstanceOf[Context[tpe]].get[tpe] }
            loop(rest, acc :+ g.asExprOf[Any])
        }
    }
    loop(ctxTypes, Nil)
  }
}
