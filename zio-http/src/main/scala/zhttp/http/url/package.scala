package zhttp.http
import zhttp.http.URL.{Fragment, Location}

import scala.StringContext._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

package object url {

  implicit class UrlInterpolator(val sc: StringContext) extends AnyVal {
    //  implicit class UrlWrapper(val sc: StringContext) extends AnyVal {
    //    def url(subs: Any*): URL = {
    //      val pit = sc.parts.iterator
    //      val sit = subs.iterator
    //      val sb  = new java.lang.StringBuilder(pit.next())
    //      while (sit.hasNext) {
    //        sb.append(sit.next().toString)
    //        sb.append(pit.next())
    //      }
    //      URL.fromString(sb.toString) match {
    //        case Left(err)    => throw new IllegalArgumentException(err.message)
    //        case Right(value) => value
    //      }
    //    }
    //
    //  }

    def url(args: Any*): String = macro Impl.fs

  }
  private object Impl {

    def fs(c: Context)(args: c.Expr[Any]*): c.Expr[String] = fx(c)(args: _*)(processEscapes)

    private[this] def fx(
      c: Context,
    )(args: c.Expr[Any]*)(process: String => String): c.Expr[String] = {
      import c.universe._

      @annotation.nowarn
      implicit val liftFragment = Liftable[Fragment] { f =>
        q"_root_.zhttp.http.URL.Fragment(${f.toString})"
      }

      @annotation.nowarn
      implicit val liftLocation = Liftable[Location] { l =>
        q"_root_.zhttp.http.Location(${l.toString})"
      }
      @annotation.nowarn
      implicit val liftPath     = Liftable[Path] { p =>
        q"_root_.zhttp.http.Path(${p.toString})"
      }
      @annotation.nowarn
      implicit val liftUrl      = Liftable[URL] { u =>
        q"_root_.zhttp.http.URL(${u.path},${u.kind}, ${u.queryParams}, ${u.fragment})"
      }

      val constants = (c.prefix.tree match {
        case Apply(_, List(Apply(_, literals))) => literals
        case _                                  => List.empty
      }).collect { case Literal(Constant(s: String)) =>
        try process(s)
        catch {
          case ex: InvalidEscapeException => c.abort(c.enclosingPosition, ex.getMessage)
        }
      }

      if (args.isEmpty) c.Expr(Literal(Constant(constants.mkString)))
      else {
        val (valDeclarations, values) = args.map { arg =>
          arg.tree match {
            case tree @ Literal(Constant(_)) =>
              (EmptyTree, if (tree.tpe <:< definitions.NullTpe) q"(null: String)" else tree)
            case tree                        =>
              val name = TermName(c.freshName())
              val tpe  = if (tree.tpe <:< definitions.NullTpe) typeOf[String] else tree.tpe
              (q"val $name: $tpe = $arg", Ident(name))
          }
        }.unzip

        val stringBuilderWithAppends = constants
          .zipAll(values, "", null)
          .foldLeft(q"java.lang.StringBuilder()") { case (sb, (s, v)) =>
            val len = s.length
            if (len == 0) {
              if (v == null) sb
              else q"$sb.append($v)"
            } else if (len == 1) {
              if (v == null) q"$sb.append(${s.charAt(0)})"
              else q"$sb.append(${s.charAt(0)}).append($v)"
            } else {
              if (v == null) q"$sb.append($s)"
              else q"$sb.append($s).append($v)"
            }
          }

        URL.fromString(stringBuilderWithAppends.toString) match {
          case Left(err) =>
            c.error(c.enclosingPosition, err.message)
            c.abort(c.enclosingPosition, err.message)
          case Right(_)  =>
            c.Expr(c.typecheck(q"..$valDeclarations; $stringBuilderWithAppends.toString"))
          // c.Expr(c.typecheck(q"$value"))
        }

      }
    }
  }
}
