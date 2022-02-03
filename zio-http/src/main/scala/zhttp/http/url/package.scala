package zhttp.http
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

package object url {

  import scala.language.implicitConversions
  implicit def urlInterpolator(sc: StringContext): UrlInterpolator = new url.UrlInterpolator(sc)

}
package url {
  import zhttp.http.URL.{Fragment, Location}

  class UrlInterpolator(val sc: StringContext) {

    def url(args: Any*): URL = macro Impl.urlValidate

  }

  object Impl {

    def urlValidate(c: Context)(@annotation.nowarn args: c.Expr[Any]*): c.Expr[URL] = {
      import c.universe._

      @annotation.nowarn
      implicit val liftableScheme: Liftable[Scheme] = Liftable[Scheme] { p =>
        q"""
        _root_.zhttp.http.Scheme.fromString(${p.toString}).getOrElse(_root_.zhttp.http.Scheme.HTTP)
        """
      }

      @annotation.nowarn
      implicit val liftableFragment: Liftable[Fragment] = Liftable[Fragment] { p =>
        q"_root_.zhttp.http.URL.Fragment(${p.toString})"
      }

      @annotation.nowarn
      implicit val liftableLocation: Liftable[Location] = Liftable[Location] {
        case Location.Relative                     =>
          q"_root_.zhttp.http.URL.Location.Relative"
        case Location.Absolute(scheme, host, port) =>
          q"_root_.zhttp.http.URL.Location.Absolute($scheme, $host, $port)"
      }

      @annotation.nowarn
      implicit val liftablePath: Liftable[Path] = Liftable[Path] { p =>
        q"_root_.zhttp.http.Path(${p.toString})"
      }

      @annotation.nowarn
      implicit val liftableUrl: Liftable[URL] = Liftable[URL] { p =>
        q"_root_.zhttp.http.URL(${p.path}, ${p.kind}, ${p.queryParams}, ${p.fragment})"
      }

      c.prefix.tree match {

        case Apply(_, List(Apply(_, List(_ @Literal(Constant(const: String)))))) =>
          URL
            .fromString(const)
            .map { p => c.Expr[URL](q"$p") }
            .getOrElse(c.abort(c.enclosingPosition, "bad URL."))
        case _                                                                   =>
          c.abort(c.enclosingPosition, s"If you have to construct a dynamic URL, than use URL.fromString() instead.")
      }

    }

  }

}
