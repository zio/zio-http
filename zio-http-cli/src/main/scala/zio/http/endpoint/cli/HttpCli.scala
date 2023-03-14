package zio.http.endpoint.cli

import scala.util.Try

import zio._
import zio.cli._
import zio.json.ast._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

final case class CliRequest(
  url: URL,
  method: model.Method,
  queryParams: QueryParams,
  headers: model.Headers,
  body: Json,
) { self =>
  def addFieldToBody(prefix: List[String], value: Json) = {
    def sparseJson(prefix: List[String], json: Json): Json =
      prefix match {
        case Nil          => json
        case head :: tail => Json.Obj(Chunk(head -> sparseJson(tail, json)))
      }

    self.copy(body = self.body.merge(sparseJson(prefix, value)))
  }

  def addHeader(name: String, value: String): CliRequest =
    self.copy(headers = self.headers.addHeader(name, value))

  def addPathParam(name: String, value: String) =
    self.copy(url = self.url.copy(path = self.url.path / name / value))

  def withMethod(method: model.Method): CliRequest =
    self.copy(method = method)
}
final case class CliEndpoint[A](embed: (A, CliRequest) => CliRequest, options: Options[A]) { self =>
  type Type = A

  def ++[B](that: CliEndpoint[B]): CliEndpoint[(A, B)] =
    CliEndpoint(
      { case ((a, b), request) =>
        that.embed(b, self.embed(a, request))
      },
      self.options ++ that.options,
    )

  def transform[B](f: A => B, g: B => A): CliEndpoint[B] =
    CliEndpoint(
      (b, request) => self.embed(g(b), request),
      self.options.map(f),
    )
}
object CliEndpoint                                                                         {
  val empty = CliEndpoint[Unit]((_, request) => request, Options.Empty)

  def fromEndpoint[In, Err, Out, M <: EndpointMiddleware](endpoint: Endpoint[In, Err, Out, M]): Set[CliEndpoint[_]] =
    fromInput(endpoint.input)

  private def fromInput[Input](input: HttpCodec[_, Input]): Set[CliEndpoint[_]] =
    input.asInstanceOf[HttpCodec[_, _]] match {
      case HttpCodec.Combine(left, right, _) =>
        for {
          l <- fromInput(left)
          r <- fromInput(right)
        } yield l ++ r

      case HttpCodec.Content(schema, _)             => Set(fromSchema(schema))
      case HttpCodec.ContentStream(schema, _)       => Set(fromSchema(schema))
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
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.addHeader(name, str),
                Options.text(name),
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.addHeader(name, int.toString),
                Options.integer(name),
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) => request.addHeader(name, bool.toString),
                Options.boolean(name),
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.addHeader(name, string),
                Options.Empty,
              ),
            )
        }
      case HttpCodec.Method(codec, _)               =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method) =>
            Set(
              CliEndpoint[Unit]((_, request) => request.withMethod(method.asInstanceOf[model.Method]), Options.none),
            )
          case SimpleCodec.Unspecified()     =>
            Set.empty
        }
      case HttpCodec.Path(textCodec, Some(name), _) =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.UUIDCodec        =>
            Set(
              CliEndpoint[java.util.UUID](
                (uuid, request) => request.copy(url = request.url.copy(path = request.url.path / name / uuid.toString)),
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
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.copy(url = request.url.copy(path = request.url.path / name / str)),
                Options.text(name),
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.copy(url = request.url.copy(path = request.url.path / name / int.toString)),
                Options.integer(name),
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) =>
                  request.copy(url = request.url.copy(queryParams = request.url.queryParams.add(name, bool.toString))),
                Options.boolean(name),
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.copy(url = request.url.copy(path = request.url.path / name / string)),
                Options.Empty,
              ),
            )
        }
      case HttpCodec.Path(_, None, _)               => Set.empty // FIXME
      case HttpCodec.Query(name, textCodec, _)      =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.UUIDCodec        =>
            Set(
              CliEndpoint[java.util.UUID](
                (uuid, request) => request.addPathParam(name, uuid.toString),
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
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.addPathParam(name, str),
                Options.text(name),
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.addPathParam(name, int.toString),
                Options.integer(name),
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) => request.addPathParam(name, bool.toString),
                Options.boolean(name),
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.addPathParam(name, string),
                Options.Empty,
              ),
            )
        }
      case HttpCodec.Status(_, _)                   => Set.empty
      case HttpCodec.TransformOrFail(api, _, _)     => fromInput(api)
      case HttpCodec.WithDoc(in, _)                 => fromInput(in)
    }

  private def fromSchema[A](schema: zio.schema.Schema[A]): CliEndpoint[_] = {
    def loop(prefix: List[String], schema: zio.schema.Schema[A]): CliEndpoint[_] =
      schema match {
        case record: Schema.Record[_]                                                  => ???
        case enumeration: Schema.Enum[_]                                               => ???
        case Schema.Primitive(standardType, _)                                         =>
          standardType match {
            case StandardType.InstantType        =>
              CliEndpoint[java.time.Instant](
                (instant, request) => request.addFieldToBody(prefix, Json.Str(instant.toString())),
                Options.instant(prefix.mkString(".")), // FIXME
              )
            case StandardType.UnitType           =>
              CliEndpoint.empty
            case StandardType.PeriodType         =>
              CliEndpoint[java.time.Period](
                (period, request) => request.addFieldToBody(prefix, Json.Str(period.toString())),
                Options.period(prefix.mkString(".")), // FIXME
              )
            case StandardType.LongType           =>
              CliEndpoint[BigInt](
                (
                  long,
                  request,
                ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(long))), // FIXME
                Options.integer(prefix.mkString(".")),                           // FIXME
              )
            case StandardType.StringType         =>
              CliEndpoint[String](
                (str, request) => request.addFieldToBody(prefix, Json.Str(str)),
                Options.text(prefix.mkString(".")), // FIXME
              )
            case StandardType.UUIDType           =>
              CliEndpoint[String](
                (uuid, request) => request.addFieldToBody(prefix, Json.Str(uuid.toString())),
                Options.text(prefix.mkString(".")), // FIXME
              )
            case StandardType.ByteType           =>
              CliEndpoint[BigInt](
                (byte, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(byte))), // FIXME
                Options.integer(prefix.mkString(".")),                                         // FIXME
              )
            case StandardType.OffsetDateTimeType =>
              CliEndpoint[java.time.OffsetDateTime](
                (
                  offsetDateTime,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(offsetDateTime.toString())),
                Options.offsetDateTime(prefix.mkString(".")), // FIXME
              )
            case StandardType.LocalDateType      =>
              CliEndpoint[java.time.LocalDate](
                (
                  localDate,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(localDate.toString())),
                Options.localDate(prefix.mkString(".")), // FIXME
              )
            case StandardType.OffsetTimeType     =>
              CliEndpoint[java.time.OffsetTime](
                (
                  offsetTime,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(offsetTime.toString())),
                Options.offsetTime(prefix.mkString(".")), // FIXME
              )
            case StandardType.FloatType          =>
              CliEndpoint[BigDecimal](
                (float, request) => request.addFieldToBody(prefix, Json.Num(float)), // FIXME
                Options.decimal(prefix.mkString(".")),                               // FIXME
              )
            case StandardType.BigDecimalType     =>
              CliEndpoint[BigDecimal](
                (bigDecimal, request) => request.addFieldToBody(prefix, Json.Num(bigDecimal)),
                Options.decimal(prefix.mkString(".")), // FIXME
              )
            case StandardType.BigIntegerType     =>
              CliEndpoint[BigInt](
                (bigInt, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(bigInt))), // FIXME
                Options.integer(prefix.mkString(".")),                                             // FIXME
              )
            case StandardType.DoubleType         =>
              CliEndpoint[BigDecimal](
                (double, request) => request.addFieldToBody(prefix, Json.Num(double)), // FIXME
                Options.decimal(prefix.mkString(".")),                                 // FIXME
              )
            case StandardType.BoolType           =>
              CliEndpoint[Boolean](
                (bool, request) => request.addFieldToBody(prefix, Json.Bool(bool)),
                Options.boolean(prefix.mkString(".")), // FIXME
              )
            case StandardType.CharType           =>
              CliEndpoint[String](
                (char, request) => request.addFieldToBody(prefix, Json.Str(char.toString())), // FIXME
                Options.text(prefix.mkString(".")),                                           // FIXME
              )
            case StandardType.ZoneOffsetType     =>
              CliEndpoint[java.time.ZoneOffset](
                (
                  zoneOffset,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(zoneOffset.toString())),
                Options.zoneOffset(prefix.mkString(".")), // FIXME
              )
            case StandardType.YearMonthType      =>
              CliEndpoint[java.time.YearMonth](
                (
                  yearMonth,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(yearMonth.toString())),
                Options.yearMonth(prefix.mkString(".")), // FIXME
              )
            case StandardType.BinaryType         => ???
            case StandardType.LocalTimeType      =>
              CliEndpoint[java.time.LocalTime](
                (
                  localTime,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(localTime.toString())),
                Options.localTime(prefix.mkString(".")), // FIXME
              )
            case StandardType.ZoneIdType         =>
              CliEndpoint[java.time.ZoneId](
                (
                  zoneId,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(zoneId.toString())),
                Options.zoneId(prefix.mkString(".")), // FIXME
              )
            case StandardType.ZonedDateTimeType  =>
              CliEndpoint[java.time.ZonedDateTime](
                (
                  zonedDateTime,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(zonedDateTime.toString())),
                Options.zonedDateTime(prefix.mkString(".")), // FIXME
              )
            case StandardType.DayOfWeekType      =>
              CliEndpoint[BigInt](
                (
                  dayOfWeek,
                  request,
                ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(dayOfWeek))), // FIXME
                Options.integer(prefix.mkString(".")),                                // FIXME
              )
            case StandardType.DurationType       =>
              CliEndpoint[BigInt](
                (
                  duration,
                  request,
                ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(duration))), // FIXME
                Options.integer(prefix.mkString(".")),                               // FIXME
              )
            case StandardType.IntType            =>
              CliEndpoint[BigInt](
                (
                  int,
                  request,
                ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(int))), // FIXME
                Options.integer(prefix.mkString(".")),                          // FIXME
              )
            case StandardType.MonthDayType       =>
              CliEndpoint[java.time.MonthDay](
                (
                  monthDay,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(monthDay.toString())),
                Options.monthDay(prefix.mkString(".")), // FIXME
              )
            case StandardType.ShortType          =>
              CliEndpoint[BigInt](
                (
                  short,
                  request,
                ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(short))), // FIXME
                Options.integer(prefix.mkString(".")),                            // FIXME
              )
            case StandardType.LocalDateTimeType  =>
              CliEndpoint[java.time.LocalDateTime](
                (
                  localDateTime,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(localDateTime.toString())),
                Options.localDateTime(prefix.mkString(".")), // FIXME
              )
            case StandardType.MonthType          =>
              CliEndpoint[String](
                (
                  month,
                  request,
                ) => request.addFieldToBody(prefix, Json.Str(month)), // FIXME
                Options.text(prefix.mkString(".")),                   // FIXME
              )
            case StandardType.YearType           =>
              CliEndpoint[BigInt](
                (
                  year,
                  request,
                ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(year))), // FIXME
                Options.integer(prefix.mkString(".")),                           // FIXME
              )
          }
        case Schema.Fail(message, annotations)                                         => ???
        case Schema.Map(keySchema, valueSchema, annotations)                           => ???
        case Schema.Sequence(elementSchema, fromChunk, toChunk, annotations, identity) => ???
        case Schema.Set(elementSchema, annotations)                                    => ???
        case Schema.Lazy(schema0)                                                      => ???
        case Schema.Dynamic(annotations)                                               => ???
        case Schema.Either(left, right, annotations)                                   => ???
        case Schema.Optional(schema, _)                                                => fromSchema(schema) // FIXME
        case Schema.Tuple2(left, right, annotations)                                   => ???
        case Schema.Transform(schema, _, _, _, _)                                      => fromSchema(schema)
      }

    loop(List.empty, schema)
  }
}

final case class CliRoutes[A](commands: Chunk[Command[A]])

/*
GET    /users                               cli get     users --id 1                                     Command[Long]
GET    /users/{id}                          cli get     users --id 1                                     Command[Long]
DELETE /users/{id}                          cli delete  users --id 1                                     Command[Long]
POST   /users                               cli create  users --email test@test.com --name Jorge         Command[(String, String)]
PUT    /users/{id}                          cli update  users --id 1                                     Command[Long]
GET    /users?order=asc                     cli get     users --order asc                                Command[String]
GET    /users/{group}/{id}?order=desc       cli get     users --group 1 --id 100 --order desc            Command[(Long, Long, String)]

Problem: How to unify all subcommands under a common parent type?
Command#subcommands requires that all subcommands have the same type
 */
