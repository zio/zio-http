package zhttp.macros

import scala.reflect.macros.whitebox
import scala.language.experimental.macros

object QueryParamsSupport {

  def decode[T](raw: Map[String, List[String]]): Either[String, T] = macro Impl.decode[T]

  object Impl {

    def decode[T: c.WeakTypeTag](c: whitebox.Context)(raw: c.Expr[T]): c.Expr[Either[String, T]] = {
      import c.universe._
      val caseClassType: c.universe.Type = weakTypeOf[T]
      c.Expr[Either[String, T]](q"""
                 import _root_.zio.schema.Schema
                 import _root_.zio.schema.DeriveSchema
                 import _root_.zhttp.http.query.QueryParams.CaseClassDecoder
                 
                 implicit val schema: Schema[$caseClassType] = DeriveSchema.gen[$caseClassType]
                 CaseClassDecoder.decode(schema, $raw)
         """)

    }
  }

}
