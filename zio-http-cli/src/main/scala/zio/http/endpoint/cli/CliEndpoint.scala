package zio.http.endpoint.cli

import scala.util.Try

import zio.cli._
import zio.json.ast._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

private[cli] final case class CliEndpoint[A](
  embed: (A, CliRequest) => CliRequest,
  options: Options[A],
  commandNameSegments: List[Either[Method, String]],
  doc: Doc,
) {
  self =>
  type Type = A

  def ++[B](that: CliEndpoint[B]): CliEndpoint[(A, B)] =
    CliEndpoint(
      { case ((a, b), request) =>
        that.embed(b, self.embed(a, request))
      },
      self.options ++ that.options,
      self.commandNameSegments ++ that.commandNameSegments,
      self.doc,
    )

  def ??(doc: Doc): CliEndpoint[A] = self.copy(doc = doc)

  lazy val commandName: String = {
    self.commandNameSegments
      .sortBy(_.isRight)
      .map {
        case Right(pathSegment) => pathSegment
        case Left(Method.POST)  => "create"
        case Left(Method.PUT)   => "update"
        case Left(method)       => method.name.toLowerCase
      }
      .mkString("-")
  }

  def describeOptions(description: String) = self.copy(options = options ?? description)

  lazy val optional: CliEndpoint[Option[A]] =
    CliEndpoint(
      {
        case (Some(a), request) => self.embed(a, request)
        case (None, request)    => request
      },
      self.options.optional,
      self.commandNameSegments,
      self.doc,
    )

  def transform[B](f: A => B, g: B => A): CliEndpoint[B] =
    CliEndpoint(
      (b, request) => self.embed(g(b), request),
      self.options.map(f),
      self.commandNameSegments,
      self.doc,
    )
}
private[cli] object CliEndpoint {
  def fromEndpoint[P, In, Err, Out, M <: EndpointMiddleware](
    endpoint: Endpoint[P, In, Err, Out, M],
  ): Set[CliEndpoint[_]] =
    fromInput(endpoint.input).map(_ ?? endpoint.doc)

  private def fromInput[Input](input: HttpCodec[_, Input]): Set[CliEndpoint[_]] =
    input.asInstanceOf[HttpCodec[_, _]] match {
      case HttpCodec.Combine(left, right, _) =>
        val leftCliEndpoints  = fromInput(left)
        val rightCliEndpoints = fromInput(right)

        if (leftCliEndpoints.isEmpty) rightCliEndpoints
        else
          for {
            l <- fromInput(left)
            r <- fromInput(right)
          } yield l ++ r

      case HttpCodec.Content(schema, _, _, _)       => fromSchema(schema)
      case HttpCodec.ContentStream(schema, _, _, _) => fromSchema(schema)
      case HttpCodec.Empty                          => Set.empty
      case HttpCodec.Fallback(left, right)          => fromInput(left) ++ fromInput(right)
      case HttpCodec.Halt                           => Set.empty
      case HttpCodec.Header(name, textCodec, _)     =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.UUIDCodec        =>
            Set(
              CliEndpoint[java.util.UUID](
                (uuid, request) => request.addHeader(name, uuid.toString),
                Options
                  .text(name)
                  .mapOrFail(str =>
                    Try(java.util.UUID.fromString(str)).toEither.left.map { error =>
                      ValidationError(
                        ValidationErrorType.InvalidValue,
                        HelpDoc.p(HelpDoc.Span.code(error.getMessage())),
                      )
                    },
                  ),
                List.empty,
                Doc.empty,
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.addHeader(name, str),
                Options.text(name),
                List.empty,
                Doc.empty,
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.addHeader(name, int.toString),
                Options.integer(name),
                List.empty,
                Doc.empty,
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) => request.addHeader(name, bool.toString),
                Options.boolean(name),
                List.empty,
                Doc.empty,
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.addHeader(name, string),
                Options.Empty,
                List.empty,
                Doc.empty,
              ),
            )
        }
      case HttpCodec.Method(codec, _)               =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.method(method.asInstanceOf[Method]),
                Options.none,
                List(Left(method.asInstanceOf[Method])),
                Doc.empty,
              ),
            )
          case SimpleCodec.Unspecified()     =>
            Set.empty
        }
      case HttpCodec.Path(pathCodec, _) =>
        pathCodec.segments.toSet.map { 
          case SegmentCodec.UUID        =>
            (
              CliEndpoint[java.util.UUID](
                (uuid, request) => request.addPathParam(uuid.toString),
                Options
                  .text(name)
                  .mapOrFail(str =>
                    Try(java.util.UUID.fromString(str)).toEither.left.map { error =>
                      ValidationError(
                        ValidationErrorType.InvalidValue,
                        HelpDoc.p(HelpDoc.Span.code(error.getMessage())),
                      )
                    },
                  ),
                List.empty,
                Doc.empty,
              ),
            )
          case SegmentCodec.Text      =>
            (
              CliEndpoint[String](
                (str, request) => request.addPathParam(str),
                Options.text(name),
                List.empty,
                Doc.empty,
              ),
            )
          case SegmentCodec.IntSeg         =>
            (
              CliEndpoint[BigInt](
                (int, request) => request.addPathParam(int.toString),
                Options.integer(name),
                List.empty,
                Doc.empty,
              ),
            )
          case SegmentCodec.BoolSeg     =>
            (
              CliEndpoint[Boolean](
                (bool, request) => request.addPathParam(bool.toString),
                Options.boolean(name),
                List.empty,
                Doc.empty,
              ),
            )
          case SegmentCodec.Literal(string) =>
            (
              CliEndpoint[Unit](
                (_, request) => request.addPathParam(string),
                Options.Empty,
                List(Right(string)),
                Doc.empty,
              ),
            )
        }
      case HttpCodec.Status(_, _)                   => Set.empty
      case HttpCodec.TransformOrFail(api, _, _)     => fromInput(api)
      case HttpCodec.WithDoc(in, doc)               => fromInput(in).map(_ describeOptions doc.toPlaintext())
      case HttpCodec.WithExamples(in, _)            => fromInput(in)
    }

  private def fromSchema(schema: zio.schema.Schema[_]): Set[CliEndpoint[_]] = {
    def loop(prefix: List[String], schema: zio.schema.Schema[_]): Set[CliEndpoint[_]] =
      schema match {
        case record: Schema.Record[_]             =>
          Set(
            record.fields
              .foldLeft(Set.empty[CliEndpoint[_]]) { (cliEndpoints, field) =>
                cliEndpoints ++ loop(prefix :+ field.name, field.schema).map { cliEndpoint =>
                  field.annotations.headOption match {
                    case Some(description) => cliEndpoint describeOptions description.asInstanceOf[description].text
                    case None              => cliEndpoint
                  }
                }
              }
              .reduce(_ ++ _), // TODO review the case of nested sealed trait inside case class
          )
        case enumeration: Schema.Enum[_]          =>
          enumeration.cases.foldLeft(Set.empty[CliEndpoint[_]]) { (cliEndpoints, enumCase) =>
            cliEndpoints ++ loop(prefix, enumCase.schema)
          }
        case Schema.Primitive(standardType, _)    =>
          standardType match {
            case StandardType.InstantType        =>
              Set(
                CliEndpoint[java.time.Instant](
                  (instant, request) => request.addFieldToBody(prefix, Json.Str(instant.toString())),
                  Options.instant(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.UnitType           => Set.empty
            case StandardType.PeriodType         =>
              Set(
                CliEndpoint[java.time.Period](
                  (period, request) => request.addFieldToBody(prefix, Json.Str(period.toString())),
                  Options.period(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.LongType           =>
              Set(
                CliEndpoint[BigInt](
                  (
                    long,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(long))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.StringType         =>
              Set(
                CliEndpoint[String](
                  (str, request) => request.addFieldToBody(prefix, Json.Str(str)),
                  Options.text(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.UUIDType           =>
              Set(
                CliEndpoint[String](
                  (uuid, request) => request.addFieldToBody(prefix, Json.Str(uuid.toString())),
                  Options.text(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.ByteType           =>
              Set(
                CliEndpoint[BigInt](
                  (byte, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(byte))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.OffsetDateTimeType =>
              Set(
                CliEndpoint[java.time.OffsetDateTime](
                  (
                    offsetDateTime,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(offsetDateTime.toString())),
                  Options.offsetDateTime(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.LocalDateType      =>
              Set(
                CliEndpoint[java.time.LocalDate](
                  (
                    localDate,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(localDate.toString())),
                  Options.localDate(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.OffsetTimeType     =>
              Set(
                CliEndpoint[java.time.OffsetTime](
                  (
                    offsetTime,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(offsetTime.toString())),
                  Options.offsetTime(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.FloatType          =>
              Set(
                CliEndpoint[BigDecimal](
                  (float, request) => request.addFieldToBody(prefix, Json.Num(float)),
                  Options.decimal(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.BigDecimalType     =>
              Set(
                CliEndpoint[BigDecimal](
                  (bigDecimal, request) => request.addFieldToBody(prefix, Json.Num(bigDecimal)),
                  Options.decimal(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.BigIntegerType     =>
              Set(
                CliEndpoint[BigInt](
                  (bigInt, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(bigInt))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.DoubleType         =>
              Set(
                CliEndpoint[BigDecimal](
                  (double, request) => request.addFieldToBody(prefix, Json.Num(double)),
                  Options.decimal(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.BoolType           =>
              Set(
                CliEndpoint[Boolean](
                  (bool, request) => request.addFieldToBody(prefix, Json.Bool(bool)),
                  Options.boolean(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.CharType           =>
              Set(
                CliEndpoint[String](
                  (char, request) => request.addFieldToBody(prefix, Json.Str(char.toString())),
                  Options.text(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.ZoneOffsetType     =>
              Set(
                CliEndpoint[java.time.ZoneOffset](
                  (
                    zoneOffset,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(zoneOffset.toString())),
                  Options.zoneOffset(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.YearMonthType      =>
              Set(
                CliEndpoint[java.time.YearMonth](
                  (
                    yearMonth,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(yearMonth.toString())),
                  Options.yearMonth(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.BinaryType         =>
              Set(
                CliEndpoint[java.nio.file.Path](
                  ???, // TODO modify CliRequest so it can store a binary body and not just Json
                  Options.file(prefix.mkString("."), Exists.Yes),
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.LocalTimeType      =>
              Set(
                CliEndpoint[java.time.LocalTime](
                  (
                    localTime,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(localTime.toString())),
                  Options.localTime(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.ZoneIdType         =>
              Set(
                CliEndpoint[java.time.ZoneId](
                  (
                    zoneId,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(zoneId.toString())),
                  Options.zoneId(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.ZonedDateTimeType  =>
              Set(
                CliEndpoint[java.time.ZonedDateTime](
                  (
                    zonedDateTime,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(zonedDateTime.toString())),
                  Options.zonedDateTime(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.DayOfWeekType      =>
              Set(
                CliEndpoint[BigInt](
                  (
                    dayOfWeek,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(dayOfWeek))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.DurationType       =>
              Set(
                CliEndpoint[BigInt](
                  (
                    duration,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(duration))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.IntType            =>
              Set(
                CliEndpoint[BigInt](
                  (
                    int,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(int))), // FIXME
                  Options.integer(prefix.mkString(".")),                          // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.MonthDayType       =>
              Set(
                CliEndpoint[java.time.MonthDay](
                  (
                    monthDay,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(monthDay.toString())),
                  Options.monthDay(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.ShortType          =>
              Set(
                CliEndpoint[BigInt](
                  (
                    short,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(short))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.LocalDateTimeType  =>
              Set(
                CliEndpoint[java.time.LocalDateTime](
                  (
                    localDateTime,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(localDateTime.toString())),
                  Options.localDateTime(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.MonthType          =>
              Set(
                CliEndpoint[String](
                  (
                    month,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Str(month)),
                  Options.text(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
            case StandardType.YearType           =>
              Set(
                CliEndpoint[BigInt](
                  (
                    year,
                    request,
                  ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(year))),
                  Options.integer(prefix.mkString(".")), // FIXME
                  List.empty,
                  Doc.empty,
                ),
              )
          }
        case Schema.Fail(_, _)                    => Set.empty
        case Schema.Map(_, _, _)                  => ??? // TODO
        case Schema.Sequence(_, _, _, _, _)       => ??? // TODO
        case Schema.Set(_, _)                     => ??? // TODO
        case Schema.Lazy(schema0)                 => loop(prefix, schema0())
        case Schema.Dynamic(_)                    => Set.empty
        case Schema.Either(left, right, _)        => loop(prefix, left) ++ loop(prefix, right)
        case Schema.Optional(schema, _)           => loop(prefix, schema).map(_.optional)
        case Schema.Tuple2(left, right, _)        =>
          for {
            l <- loop(prefix, left)
            r <- loop(prefix, right)
          } yield l ++ r
        case Schema.Transform(schema, _, _, _, _) => loop(prefix, schema)
      }

    loop(List.empty, schema)
  }
}
