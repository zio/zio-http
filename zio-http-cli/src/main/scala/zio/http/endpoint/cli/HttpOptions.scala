package zio.http.endpoint.cli

import scala.util.Try

import zio.cli.Options

import zio.http.codec.TextCodec
import zio.http.MediaType
import zio.http.Body
import zio.schema._
import zio.json.ast._
import java.nio.file.Path
import zio.http.FormField
import zio.cli._

private[cli] sealed trait HttpOptions {

  def transform(request: Options[CliRequest]): Options[CliRequest]

}

private[cli] object HttpOptions {

    /*
     * It is possible to specify a body writing directly on the terminal, from a file or the body of the response from another CliRequest.
     */

    case class Body[A](name: String, mediaType: Option[MediaType], schema: Schema[A]) extends HttpOptions {

      
      lazy val options: Options[Either[Either[java.nio.file.Path, String], Json]] = {
        val written: Options[Json] = fromSchema(schema)
        val fromFile = Options.file("f:"+name)
        val fromUrl = Options.text("url:"+name)

        fromFile orElseEither fromUrl orElseEither written
        
      }

      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        (request ++ options).map {
          case (cliRequest, value) => {
            val formField: Either[Either[(String, java.nio.file.Path, MediaType), String], FormField] = 
              value match {
                case Left(Left(file)) => mediaType match {
                  case Some(mediaType) => Left(Left((name, file, mediaType)))
                  case None => Right(FormField.simpleField("foo", "foo"))
                }
                case Left(Right(url)) => Left(Right(url))
                case Right(a) => Right(FormField.textField(name, a.toString()))
              }
              value match {
                case Left(Left(file)) => mediaType match {
                  case Some(mediaType) => cliRequest.addBody(formField)
                  case None => cliRequest
                }
                case _ => cliRequest.addBody(formField)
              }
          }
        }

        lazy val emptyJson: Options[Json] = Options.Empty.map(_ => Json.Obj())
  

  private def fromSchema(schema: zio.schema.Schema[_]): Options[Json] = {

    

    def loop(prefix: List[String], schema: zio.schema.Schema[_]): Options[Json] =
      schema match {
        case record: Schema.Record[_]             =>
            record.fields
              .foldLeft(emptyJson) { (options, field) =>
                val fieldOptions: Options[Json] = field.annotations.headOption match {
                    case Some(description) => loop(prefix :+ field.name, field.schema) ?? description.asInstanceOf[description].text
                    case None              => loop(prefix :+ field.name, field.schema)
                  } 
                merge(options, fieldOptions)
              } // TODO review the case of nested sealed trait inside case class
        case enumeration: Schema.Enum[_]          =>
          enumeration.cases.foldLeft(emptyJson) {
            case (options, enumCase) => merge(options, loop(prefix, enumCase.schema))
          }

        case Schema.Primitive(standardType, _)    => fromPrimitive(prefix, standardType)
          
        case Schema.Fail(_, _)                    => emptyJson
        case Schema.Map(_, _, _)                  => ??? // TODO
        case Schema.Sequence(_, _, _, _, _)       => ??? // TODO
        case Schema.Set(_, _)                     => ??? // TODO
        case Schema.Lazy(schema0)                 => loop(prefix, schema0())
        case Schema.Dynamic(_)                    => emptyJson
        case Schema.Either(left, right, _)        => 
          (loop(prefix, left) orElseEither loop(prefix, right)).map(_.merge)
        case Schema.Optional(schema, _)           => loop(prefix, schema).optional.map {
          case Some(json) => json
          case None => Json.Obj()
        }
        case Schema.Tuple2(left, right, _)        =>
          merge(loop(prefix, left), loop(prefix, right))
        case Schema.Transform(schema, _, _, _, _) => loop(prefix, schema)
      }

      def merge(opt1: Options[Json], opt2: Options[Json]): Options[Json] =
        (opt1 ++ opt2).map{ case (a, b) => Json.Arr(a,b) }

    loop(List.empty, schema)
  }

  implicit def toJson[A](options: Options[A]): Options[Json] = options.map( value => Json.Str(value.toString()))

  def fromPrimitive(prefix: List[String], standardType: StandardType[_]): Options[Json] = standardType match {
            case StandardType.InstantType        => Options.instant(prefix.mkString("."))
            case StandardType.UnitType           => emptyJson
            case StandardType.PeriodType         => Options.period(prefix.mkString("."))
            case StandardType.LongType           =>
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.StringType         => Options.text(prefix.mkString("."))
            case StandardType.UUIDType           => Options.text(prefix.mkString("."))
            case StandardType.ByteType           =>
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.OffsetDateTimeType => Options.offsetDateTime(prefix.mkString("."))
            case StandardType.LocalDateType      => Options.localDate(prefix.mkString("."))
            case StandardType.OffsetTimeType     => Options.decimal(prefix.mkString("."))
            case StandardType.FloatType    =>
              Options.decimal(prefix.mkString(".")).map( value => Json.Num(value))
            case StandardType.BigDecimalType     =>
              Options.decimal(prefix.mkString(".")).map( value => Json.Num(value))
            case StandardType.BigIntegerType     =>
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.DoubleType         =>
              Options.decimal(prefix.mkString(".")).map( value => Json.Num(value))
            case StandardType.BoolType           =>
              Options.boolean(prefix.mkString(".")).map( value => Json.Bool(value))
            case StandardType.CharType           => Options.text(prefix.mkString("."))
            case StandardType.ZoneOffsetType     => Options.zoneOffset(prefix.mkString("."))
            case StandardType.YearMonthType      => Options.yearMonth(prefix.mkString("."))
            case StandardType.BinaryType         => emptyJson
            case StandardType.LocalTimeType      => Options.localTime(prefix.mkString("."))
            case StandardType.ZoneIdType         => Options.zoneId(prefix.mkString("."))
            case StandardType.ZonedDateTimeType  => Options.zonedDateTime(prefix.mkString("."))
            case StandardType.DayOfWeekType      => 
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.DurationType       => 
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.IntType            => 
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.MonthDayType       => Options.monthDay(prefix.mkString("."))
            case StandardType.ShortType          => 
              Options.integer(prefix.mkString(".")).map( value => Json.Num(BigDecimal(value)))
            case StandardType.LocalDateTimeType  => Options.localDateTime(prefix.mkString("."))
            case StandardType.MonthType          => Options.text(prefix.mkString("."))
            case StandardType.YearType           => Options.integer(prefix.mkString("."))
          }

    }


    sealed trait HeaderOptions extends HttpOptions
    case class Header(name: String, textCodec: TextCodec[_]) extends HeaderOptions {

      lazy val options: Options[_] = optionsFromCodec(textCodec)(name)
    
      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        (request ++ options).map {
          case (cliRequest, value) =>
            if(true) cliRequest.addHeader(name, value.toString())
            else cliRequest
        }

    }

    case class HeaderConstant(name: String, value: String) extends HeaderOptions {

      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        request.map(_.addHeader(name, value))
      
    }

    sealed trait URLOptions extends HttpOptions {
      val tag: String
    }
    case class Path(name: String, textCodec: TextCodec[_]) extends URLOptions {

      lazy val options: Options[_] = optionsFromCodec(textCodec)(name)

      override val tag = "/"+name
    
      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        (request ++ options).map {
          case (cliRequest, value) =>
            if(true) cliRequest.addPathParam(name)
            else cliRequest
        }

    }

    case class PathConstant(name: String) extends URLOptions {
      override val tag = "/"+name
      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        request.map(_.addPathParam(name))

    }

    case class Query(name: String, textCodec: TextCodec[_]) extends URLOptions {
      override val tag = "/"+name+"?"
      lazy val options: Options[_] = optionsFromCodec(textCodec)(name)
    
      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        (request ++ options).map {
          case (cliRequest, value) =>
            if(true) cliRequest.addQueryParam(name, value.toString())
            else cliRequest
        }

    }

    case class QueryConstant(name: String, value: String) extends URLOptions {
      override val tag = "/"+name+"?"+value
      override def transform(request: Options[CliRequest]): Options[CliRequest] = 
        request.map(_.addQueryParam(name, value))

    }

    def optionsFromCodec[A](textCodec: TextCodec[A]): (String => Options[_]) =
      textCodec.asInstanceOf[TextCodec[_]] match {
        case TextCodec.UUIDCodec        => 
          Options.text(_)
            .mapOrFail(str =>
              Try(java.util.UUID.fromString(str)).toEither.left.map { error =>
                ValidationError(
                  ValidationErrorType.InvalidValue,
                  HelpDoc.p(HelpDoc.Span.code(error.getMessage())),
                )
              }
            )
        case TextCodec.StringCodec      => Options.text(_)
        case TextCodec.IntCodec         => Options.integer(_)
        case TextCodec.BooleanCodec     => Options.boolean(_)
        case _                          => ( _ => Options.Empty)
      }

}

