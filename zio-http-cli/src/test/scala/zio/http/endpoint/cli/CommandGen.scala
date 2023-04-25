package zio.http.endpoint.cli

import zio.cli._
import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.cli.CliRepr.HelpRepr
import zio.http.endpoint.cli.EndpointGen._

/**
 * Constructs a Gen[Any, CliRepr[Endpoint, HelpDoc]]
 */

object CommandGen {

  def getSegment(segment: SegmentCodec[_]): (String, String) = {
    def fromSegment[A](segment: SegmentCodec[A]): (String, String) =
      segment match {
        case SegmentCodec.UUID(name)    => (name, "text")
        case SegmentCodec.Text(name)    => (name, "text")
        case SegmentCodec.IntSeg(name)  => (name, "integer")
        case SegmentCodec.LongSeg(name) => (name, "integer")
        case SegmentCodec.BoolSeg(name) => (name, "boolean")
        case SegmentCodec.Literal(_)    => ("", "")
        case SegmentCodec.Trailing      => ("", "")
        case SegmentCodec.Empty         => ("", "")
      }
    fromSegment(segment)
  }

  lazy val anyEndpoint: Gen[Any, HelpRepr[Endpoint[_, _, _, _, EndpointMiddleware.None]]] =
    anyCodec
      .map(_.map2(getCommand(_)))
      .map(_.map(fromInputCodec(Doc.empty, _)))

  def getCommand(cliEndpoint: CliEndpoint): HelpDoc = {

    // Ensambling correct options
    val urlOptions: List[String] = cliEndpoint.url.filter {
      case _: HttpOptions.Constant => false
      case _                       => true
    }.map {
      case HttpOptions.Path(pathCodec, _)        =>
        pathCodec.segments.toList.flatMap { case segment =>
          getSegment(segment) match {
            case (_, "")           => Nil
            case (name, "boolean") => s"[${getName(name, "")}]" :: Nil
            case (name, codec)     => s"${getName(name, "")} $codec" :: Nil
          }
        }
      case HttpOptions.Query(name, textCodec, _) =>
        getType(textCodec) match {
          case ""    => s"[${getName(name, "")}]" :: Nil
          case codec => s"${getName(name, "")} $codec" :: Nil
        }
      case _                                     => Nil
    }.foldRight(List[String]())(_ ++ _)

    val headersOptions = cliEndpoint.headers.filter {
      case _: HttpOptions.Constant => false
      case _                       => true
    }.map {
      case HttpOptions.Header(name, textCodec, _) =>
        getType(textCodec) match {
          case ""    => s"[${getName(name, "")}]"
          case codec => s"${getName(name, "")} $codec"
        }
      case _                                      => ""

    }

    val bodyOptions = cliEndpoint.body.map { case HttpOptions.Body(name, _, schema, _) =>
      bodyParameters(name, schema)
    }

    val options = urlOptions ++ headersOptions ++ bodyOptions

    // Ensambling correct command
    val urlCommand: List[String] =
      cliEndpoint.url.filter {
        case _: HttpOptions.Constant => true
        case _                       => false
      }
        .map(_.name)

    def getMethod(method: Method): String =
      method match {
        case Method.POST => "create"
        case Method.PUT  => "update"
        case method      => method.name.toLowerCase
      }

    val commandName: String = (getMethod(cliEndpoint.methods) :: urlCommand).mkString("-")

    val command = List(commandName, options.mkString(" "), cliEndpoint.doc.toPlaintext())
      .filter(_ != "")

    HelpDoc.h1("Commands") + HelpDoc.p(command.mkString(" ") + " ")
  }

  // Check if name is an alias and returns corresponding representation
  def getName(name: String, prefix: String): String =
    if (prefix == "") if (name.length == 1) s"-$name" else s"--$name"
    else if (name == "") s"-$prefix"
    else s"--$prefix-$name"

  def getType[A](textCodec: TextCodec[A]): String =
    textCodec match {
      case TextCodec.UUIDCodec    => "text"
      case TextCodec.StringCodec  => "text"
      case TextCodec.IntCodec     => "integer"
      case TextCodec.LongCodec    => "integer"
      case TextCodec.BooleanCodec => ""
      case _                      => ""
    }

  def getPrimitive(schema: Schema[_]): String =
    schema match {
      case Schema.Primitive(standardType, _) =>
        standardType match {
          case StandardType.InstantType        => "instant"
          case StandardType.UnitType           => "empty"
          case StandardType.PeriodType         => "period"
          case StandardType.LongType           => "integer"
          case StandardType.StringType         => "text"
          case StandardType.UUIDType           => "text"
          case StandardType.ByteType           => "integer"
          case StandardType.OffsetDateTimeType => "offset-date-tim"
          case StandardType.LocalDateType      => "date"
          case StandardType.OffsetTimeType     => "offset-time"
          case StandardType.FloatType          => "decimal"
          case StandardType.BigDecimalType     => "decimal"
          case StandardType.BigIntegerType     => "integer"
          case StandardType.DoubleType         => "decimal"
          case StandardType.BoolType           => "boolean"
          case StandardType.CharType           => "text"
          case StandardType.ZoneOffsetType     => "zone-offset"
          case StandardType.YearMonthType      => "year-month"
          case StandardType.BinaryType         => "non primitive"
          case StandardType.LocalTimeType      => "time"
          case StandardType.ZoneIdType         => "zone-id"
          case StandardType.ZonedDateTimeType  => "zone-date"
          case StandardType.DayOfWeekType      => "integer"
          case StandardType.DurationType       => "integer"
          case StandardType.IntType            => "integer"
          case StandardType.MonthDayType       => "month-day"
          case StandardType.ShortType          => "integer"
          case StandardType.LocalDateTimeType  => "date-time"
          case StandardType.MonthType          => "text"
          case StandardType.YearType           => "integer"
        }
      case _                                 => "non primitive"
    }

  def canBeTested(schema: Schema[_]): Boolean =
    schema match {
      case record: Schema.Record[_]    =>
        !record.fields
          .map(_.schema)
          .map(getPrimitive(_) != "non primitive")
          .contains("non primitive")
      case enumeration: Schema.Enum[_] =>
        !enumeration.cases
          .map(_.schema)
          .map(getPrimitive(_))
          .contains("non primitive")
      case _                           => true
    }

  def bodyParameters(name: String, schema: Schema[_]): String =
    schema match {
      case Schema.Primitive(StandardType.BinaryType, _)            =>
        s"${getName(name, "f")} file|${getName(name, "u")} text"
      case schema @ Schema.Primitive(_, _)                         =>
        s"${getName(name, "f")} file|${getName(name, "u")} text|${getName(name, "")} ${getPrimitive(schema)}"
      case Schema.Map(_, _, _)                                     =>
        s"${getName(name, "f")} file|${getName(name, "u")} text"
      case Schema.Set(_, _)                                        =>
        s"${getName(name, "f")} file|${getName(name, "u")} text"
      case Schema.Sequence(_, _, _, _, _)                          =>
        s"${getName(name, "f")} file|${getName(name, "u")} text"
      case record: Schema.Record[_] if canBeTested(record)         => {
        record.fields.map { case field =>
          s"${bodyParameters(s"$name.${field.name}", field.schema)}"
        }.mkString(" ")
      }
      case enumeration: Schema.Enum[_] if canBeTested(enumeration) =>
        enumeration.cases.map { case enumCase =>
          s"${bodyParameters(s"$name.${enumCase.id}", enumCase.schema)}"
        }.mkString("|")
      case Schema.Fail(_, _) | Schema.Dynamic(_)                   => ""
      case Schema.Lazy(schema)                                     => bodyParameters(name, schema())
      case Schema.Either(left, right, _)                           =>
        s"${bodyParameters(name, left)}|${bodyParameters(name, right)}"
      case Schema.Optional(schema, _)                              =>
        bodyParameters(name, schema)
      case Schema.Tuple2(left, right, _)                           =>
        s"${bodyParameters(name, left)} ${bodyParameters(name, right)}"
      case Schema.Transform(schema, _, _, _, _)                    => { bodyParameters(name, schema) }
      case _                                                       => "This schema must not be tested"
    }
}
