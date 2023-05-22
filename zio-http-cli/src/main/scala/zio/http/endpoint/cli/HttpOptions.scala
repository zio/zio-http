package zio.http.endpoint.cli

import java.nio.file.Path

import scala.language.implicitConversions
import scala.util.Try

import zio.cli._
import zio.json.ast._

import zio.schema._

import zio.http._
import zio.http.codec._

/*
 * HttpOptions is a wrapper of a transformation Options[CliRequest] => Options[CliRequest].
 * The defined HttpOptions subclasses store an information about a request (body, header and url)
 * and the transformation adds this information to a generic CliRequest wrapped in Options.
 *
 */

private[cli] sealed trait HttpOptions {

  val name: String

  def transform(request: Options[CliRequest]): Options[CliRequest]

  def ??(doc: Doc): HttpOptions

}

private[cli] object HttpOptions {

  /*
   * Subclass for Body
   * It is possible to specify a body writing directly on the terminal, from a file or the body of the response from another Request.
   * TODO implementation for getting body from URL.
   */
  final case class Body[A](
    override val name: String,
    mediaType: Option[MediaType],
    schema: Schema[A],
    doc: Doc = Doc.empty,
  ) extends HttpOptions {
    self =>

    lazy val options: Options[Retriever] = {
      val written: Options[Json] = fromSchema(schema)
      val fromFile               = Options.file("f:" + name)
      val fromUrl                = Options.text("url:" + name)

      val retriever = fromFile orElseEither fromUrl orElseEither written
      retriever.map {
        _ match {
          case Left(Left(file)) => Retriever.File(name, file, mediaType)
          case Left(Right(url)) => Retriever.URL(url)
          case Right(json)      => Retriever.Content(FormField.textField(name, json.toString()))
        }
      }
    }

    override def ??(doc: Doc): Body[A] = self.copy(doc = self.doc + doc)

    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      (request ++ options).map { case (cliRequest, retriever) =>
        cliRequest.addBody(retriever)
      }

    /*
     * Allows to specify the body with given schema using Json.
     * It does not support schemas with Binary Primitive. This can be added in a ContentCodec
     */
    private def fromSchema(schema: zio.schema.Schema[_]): Options[Json] = {

      implicit def toJson[A](options: Options[A]): Options[Json] = options.map(value => Json.Str(value.toString()))

      lazy val emptyJson: Options[Json] = Options.Empty.map(_ => Json.Obj())

      def loop(prefix: List[String], schema: zio.schema.Schema[_]): Options[Json] =
        schema match {
          case record: Schema.Record[_]    =>
            record.fields
              .foldLeft(emptyJson) { (options, field) =>
                val fieldOptions: Options[Json] = field.annotations.headOption match {
                  case Some(description) =>
                    loop(prefix :+ field.name, field.schema) ?? description.asInstanceOf[description].text
                  case None              => loop(prefix :+ field.name, field.schema)
                }
                merge(options, fieldOptions)
              } // TODO review the case of nested sealed trait inside case class
          case enumeration: Schema.Enum[_] =>
            enumeration.cases.foldLeft(emptyJson) { case (options, enumCase) =>
              merge(options, loop(prefix, enumCase.schema))
            }

          case Schema.Primitive(standardType, _) => fromPrimitive(prefix, standardType)

          case Schema.Fail(_, _)                    => emptyJson
          case Schema.Map(_, _, _)                  => ??? // TODO
          case Schema.Sequence(_, _, _, _, _)       => ??? // TODO
          case Schema.Set(_, _)                     => ??? // TODO
          case Schema.Lazy(schema0)                 => loop(prefix, schema0())
          case Schema.Dynamic(_)                    => emptyJson
          case Schema.Either(left, right, _)        =>
            (loop(prefix, left) orElseEither loop(prefix, right)).map(_.merge)
          case Schema.Optional(schema, _)           =>
            loop(prefix, schema).optional.map {
              case Some(json) => json
              case None       => Json.Obj()
            }
          case Schema.Tuple2(left, right, _)        =>
            merge(loop(prefix, left), loop(prefix, right))
          case Schema.Transform(schema, _, _, _, _) => loop(prefix, schema)
        }

      def merge(opt1: Options[Json], opt2: Options[Json]): Options[Json] =
        (opt1 ++ opt2).map { case (a, b) => Json.Arr(a, b) }

      def fromPrimitive(prefix: List[String], standardType: StandardType[_]): Options[Json] = standardType match {
        case StandardType.InstantType        => Options.instant(prefix.mkString("."))
        case StandardType.UnitType           => emptyJson
        case StandardType.PeriodType         => Options.period(prefix.mkString("."))
        case StandardType.LongType           =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.StringType         => Options.text(prefix.mkString("."))
        case StandardType.UUIDType           => Options.text(prefix.mkString("."))
        case StandardType.ByteType           =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.OffsetDateTimeType => Options.offsetDateTime(prefix.mkString("."))
        case StandardType.LocalDateType      => Options.localDate(prefix.mkString("."))
        case StandardType.OffsetTimeType     => Options.decimal(prefix.mkString("."))
        case StandardType.FloatType          =>
          Options.decimal(prefix.mkString(".")).map(value => Json.Num(value))
        case StandardType.BigDecimalType     =>
          Options.decimal(prefix.mkString(".")).map(value => Json.Num(value))
        case StandardType.BigIntegerType     =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.DoubleType         =>
          Options.decimal(prefix.mkString(".")).map(value => Json.Num(value))
        case StandardType.BoolType           =>
          Options.boolean(prefix.mkString(".")).map(value => Json.Bool(value))
        case StandardType.CharType           => Options.text(prefix.mkString("."))
        case StandardType.ZoneOffsetType     => Options.zoneOffset(prefix.mkString("."))
        case StandardType.YearMonthType      => Options.yearMonth(prefix.mkString("."))
        case StandardType.BinaryType         => emptyJson
        case StandardType.LocalTimeType      => Options.localTime(prefix.mkString("."))
        case StandardType.ZoneIdType         => Options.zoneId(prefix.mkString("."))
        case StandardType.ZonedDateTimeType  => Options.zonedDateTime(prefix.mkString("."))
        case StandardType.DayOfWeekType      =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.DurationType       =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.IntType            =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.MonthDayType       => Options.monthDay(prefix.mkString("."))
        case StandardType.ShortType          =>
          Options.integer(prefix.mkString(".")).map(value => Json.Num(BigDecimal(value)))
        case StandardType.LocalDateTimeType  => Options.localDateTime(prefix.mkString("."))
        case StandardType.MonthType          => Options.text(prefix.mkString("."))
        case StandardType.YearType           => Options.integer(prefix.mkString("."))
      }

      loop(List.empty, schema)
    }

  }

  /*
   * Subclasses for headers
   */
  sealed trait HeaderOptions extends HttpOptions {
    override def ??(doc: Doc): HeaderOptions
  }
  final case class Header(override val name: String, textCodec: TextCodec[_], doc: Doc = Doc.empty)
      extends HeaderOptions {
    self =>

    lazy val options: Options[_] = optionsFromCodec(textCodec)(name)

    override def ??(doc: Doc): Header = self.copy(doc = self.doc + doc)

    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      (request ++ options).map { case (cliRequest, value) =>
        if (true) cliRequest.addHeader(name, value.toString())
        else cliRequest
      }

  }

  final case class HeaderConstant(override val name: String, value: String, doc: Doc = Doc.empty)
      extends HeaderOptions {
    self =>

    override def ??(doc: Doc): HeaderConstant = self.copy(doc = self.doc + doc)

    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      request.map(_.addHeader(name, value))

  }

  /*
   * Subclasses for path
   */
  sealed trait URLOptions extends HttpOptions {
    val tag: String

    override def ??(doc: Doc): URLOptions
  }

  final case class Path(override val name: String, textCodec: TextCodec[_], doc: Doc = Doc.empty) extends URLOptions {
    self =>

    lazy val options: Options[_] = optionsFromCodec(textCodec)(name)

    override def ??(doc: Doc): Path = self.copy(doc = self.doc + doc)

    override val tag = "/" + name

    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      (request ++ options).map { case (cliRequest, value) =>
        if (true) cliRequest.addPathParam(value.toString())
        else cliRequest
      }

  }

  final case class PathConstant(override val name: String, doc: Doc = Doc.empty) extends URLOptions {
    self =>

    override val tag = "/" + name

    override def ??(doc: Doc): PathConstant = self.copy(doc = self.doc + doc)

    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      request.map(_.addPathParam(name))

  }

  final case class Query(override val name: String, textCodec: TextCodec[_], doc: Doc = Doc.empty) extends URLOptions {
    self =>

    override val tag             = "?" + name
    lazy val options: Options[_] = optionsFromCodec(textCodec)(name)

    override def ??(doc: Doc): Query = self.copy(doc = self.doc + doc)

    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      (request ++ options).map { case (cliRequest, value) =>
        if (true) cliRequest.addQueryParam(name, value.toString())
        else cliRequest
      }

  }

  final case class QueryConstant(override val name: String, value: String, doc: Doc = Doc.empty) extends URLOptions {
    self =>

    override val tag                                                          = "?" + name + "=" + value
    override def ??(doc: Doc): QueryConstant                                  = self.copy(doc = self.doc + doc)
    override def transform(request: Options[CliRequest]): Options[CliRequest] =
      request.map(_.addQueryParam(name, value))

  }

  private def optionsFromCodec[A](textCodec: TextCodec[A]): (String => Options[_]) =
    textCodec.asInstanceOf[TextCodec[_]] match {
      case TextCodec.UUIDCodec    =>
        Options
          .text(_)
          .mapOrFail(str =>
            Try(java.util.UUID.fromString(str)).toEither.left.map { error =>
              ValidationError(
                ValidationErrorType.InvalidValue,
                HelpDoc.p(HelpDoc.Span.code(error.getMessage())),
              )
            },
          )
      case TextCodec.StringCodec  => Options.text(_)
      case TextCodec.IntCodec     => Options.integer(_)
      case TextCodec.BooleanCodec => Options.boolean(_)
      case _                      => _ => Options.Empty
    }
}
