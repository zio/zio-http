package zhttp.http.query

import zhttp.http.QueryParameters
import zio.schema.Schema

object QueryParams {

  implicit class QueryParametersWrapper(queryParams: QueryParameters) {
    def decode[A](implicit schema: Schema[A]): Either[String, A] = QueryParams2.Decoder.decode(schema, queryParams.raw)
  }

//  private def decode[A](schema: Schema[A], raw: Map[String, List[String]]): Either[String, A] = {
//    import scala.language.implicitConversions
//
//    implicit def toEither[A0](op: Option[A0]): Either[String, A0] = op match {
//      case Some(value) => Right(value)
//      case None        => Left("The provided payload do not contains the expected value.")
//    }
//
//    def coerce[F](schema: Schema[F], f: F): F =
//      schema match {
//        case Schema.Primitive(standardType, _) =>
//          standardType match {
//            case StandardType.UnitType   => f
//            case StandardType.StringType => f
//            case StandardType.IntType    => f
//            case StandardType.LongType   => f
//            case _                       => "".asInstanceOf[F]
//          }
//        case Schema.Optional(codec, _)         => Option(coerce(codec, f)).asInstanceOf[F]
//        case Schema.Sequence(_, _, _, _)       => f
//        case Schema.Transform(codec, to, _, _) => {
//          println(codec)
//          println(to)
//          val result = to.asInstanceOf[F]
//          result
//        }
//        case lzy @ Schema.Lazy(_)              => coerce(lzy.schema, f)
//        case Schema.Enum1(c, _)                =>
//          println(c)
//          f
//        case Schema.Enum2(c1, c2, _)           =>
//          f match {
//            case c1.id => coerce(c1.codec, f).asInstanceOf[F]
//            case c2.id => c2.asInstanceOf[F]
//            case _     => "".asInstanceOf[F]
//          }
//
//        case _ => "".asInstanceOf[F]
//      }
//    schema match {
//      case Schema.CaseClass1(field1, construct, _, _) =>
//        for {
//          a <- extractField(field1.label, field1.schema, raw)
//        } yield {
//          construct(coerce(field1.schema, a))
//        }
//
//      case Schema.CaseClass2(field1, field2, construct, _, _, _)                       =>
//        for {
//          a <- extractField(field1.label, field2.schema, raw)
//          b <- extractField(field2.label, field2.schema, raw)
//        } yield construct(coerce(field1.schema, a), coerce(field2.schema, b))
//      case Schema.CaseClass3(field1, field2, field3, construct, _, _, _, _)            =>
//        for {
//          a <- extractField(field1.label, field2.schema, raw)
//          b <- extractField(field2.label, field2.schema, raw)
//          c <- extractField(field3.label, field3.schema, raw)
//        } yield construct(coerce(field1.schema, a), coerce(field2.schema, b), coerce(field3.schema, c))
//      case Schema.CaseClass4(field1, field2, field3, field4, construct, _, _, _, _, _) =>
//        for {
//          a <- extractField(field1.label, field2.schema, raw)
//          b <- extractField(field2.label, field2.schema, raw)
//          c <- extractField(field3.label, field3.schema, raw)
//          d <- extractField(field4.label, field4.schema, raw)
//        } yield construct(
//          coerce(field1.schema, a),
//          coerce(field2.schema, b),
//          coerce(field3.schema, c),
//          coerce(field4.schema, d),
//        )
//
//      case Schema.Enum1(c, _)      =>
//        println(c)
//        Left(s"The schema ${schema} is not supported")
//      case Schema.Enum2(c1, c2, _) =>
//        println(c1)
//        println(c2)
//        Left(s"The schema ${schema} is not supported")
//      case _                       => Left(s"The schema ${schema} is not supported")
//    }
//  }
//
//  private def extractField[A0](label: String, schema: Schema[A0], map: Map[String, List[String]]): Option[_] = {
//
//    if (schema.ast.dimensions == 1) map.get(label) else map.get(label).flatMap(_.headOption)
//
//  }

//  private def enumDecoder[Z, A](path: String, cases: Schema.Case[_, Z]*): Option[Z] = {
//    cases.find(_.id.toLowerCase == path)
//  }
}
