package zio.http.api.openapi

import zio.NonEmptyChunk
import zio.http.api.Doc
import zio.http.api.openapi.OpenAPI.LiteralOrExpression
import zio.http.model.Status

import java.net.URI
import java.util.Base64
import scala.language.implicitConversions // scalafix:ok;

private[openapi] object JsonRenderer {
  sealed trait Renderable[A] {
    def render(a: A): String
  }

  implicit class Renderer[T](t: T)(implicit val renderable: Renderable[T]) {
    def render: String = renderable.render(t)

    val skip: Boolean =
      t.asInstanceOf[Any] match {
        case None => true
        case _    => false
      }
  }

  def renderFields(fieldsIt: (String, Renderer[_])*): String = {
    if (fieldsIt.map(_._1).toSet.size != fieldsIt.size) {
      throw new IllegalArgumentException("Duplicate field names")
    } else {
      val fields = fieldsIt
        .filterNot(_._2.skip)
        .map { case (name, value) => s""""$name":${value.render}""" }
      s"{${fields.mkString(",")}}"
    }
  }

  private def renderKey[K](k: K)(implicit renderable: Renderable[K]) =
    if (renderable.render(k).startsWith("\"") && renderable.render(k).endsWith("\"")) renderable.render(k)
    else s""""${renderable.render(k)}""""

  implicit def stringRenderable[T <: String]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = s""""$a""""
  }

  implicit def intRenderable[T <: Int]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = a.toString
  }

  implicit def Renderable[T <: Long]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = a.toString
  }

  implicit def floatRenderable[T <: Float]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = a.toString
  }

  implicit def doubleRenderable[T <: Double]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = a.toString
  }

  implicit def booleanRenderable[T <: Boolean]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = a.toString
  }

  implicit val uriRenderable: Renderable[URI] = new Renderable[URI] {
    def render(a: URI): String = s""""${a.toString}""""
  }

  implicit val statusRenderable: Renderable[Status] = new Renderable[Status] {
    def render(a: Status): String = a.code.toString
  }

  implicit val docRenderable: Renderable[Doc] = new Renderable[Doc] {
    def render(a: Doc): String = s""""${Base64.getEncoder.encodeToString(a.toCommonMark.getBytes)}""""
  }

  implicit def openapiBaseRenderable[T <: OpenAPIBase]: Renderable[T] = new Renderable[T] {
    def render(a: T): String = a.toJson
  }

  implicit def optionRenderable[A](implicit renderable: Renderable[A]): Renderable[Option[A]] =
    new Renderable[Option[A]] {
      def render(a: Option[A]): String = a match {
        case Some(value) => renderable.render(value)
        case None        => "null"
      }
    }

  implicit def nonEmptyChunkRenderable[A](implicit renderable: Renderable[A]): Renderable[NonEmptyChunk[A]] =
    new Renderable[NonEmptyChunk[A]] {
      def render(a: NonEmptyChunk[A]): String = s"[${a.map(renderable.render).mkString(",")}]"
    }

  implicit def setRenderable[A](implicit renderable: Renderable[A]): Renderable[Set[A]] =
    new Renderable[Set[A]] {
      def render(a: Set[A]): String = s"[${a.map(renderable.render).mkString(",")}]"
    }

  implicit def listRenderable[A](implicit renderable: Renderable[A]): Renderable[List[A]] =
    new Renderable[List[A]] {
      def render(a: List[A]): String = s"[${a.map(renderable.render).mkString(",")}]"
    }

  implicit def mapRenderable[K, V](implicit rK: Renderable[K], rV: Renderable[V]): Renderable[Map[K, V]] =
    new Renderable[Map[K, V]] {
      def render(a: Map[K, V]): String =
        s"{${a.map { case (k, v) => s"${renderKey(k)}:${rV.render(v)}" }.mkString(",")}}"
    }

  implicit def tupleRenderable[A, B](implicit rA: Renderable[A], rB: Renderable[B]): Renderable[(A, B)] =
    new Renderable[(A, B)] {
      def render(a: (A, B)): String = s"{${renderKey(a._1)}:${rB.render(a._2)}}"
    }

  implicit def literalOrExpressionRenderable: Renderable[LiteralOrExpression] =
    new Renderable[LiteralOrExpression] {
      def render(a: LiteralOrExpression): String = a match {
        case LiteralOrExpression.NumberLiteral(value)  => implicitly[Renderable[Long]].render(value)
        case LiteralOrExpression.DecimalLiteral(value) => implicitly[Renderable[Double]].render(value)
        case LiteralOrExpression.StringLiteral(value)  => implicitly[Renderable[String]].render(value)
        case LiteralOrExpression.BooleanLiteral(value) => implicitly[Renderable[Boolean]].render(value)
        case LiteralOrExpression.Expression(value)     => implicitly[Renderable[String]].render(value)
      }
    }
}
