package zio.http.endpoint.cli

import java.util.UUID

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
)
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

      case HttpCodec.Content(schema, _)       => Set(fromSchema(schema))
      case HttpCodec.ContentStream(schema, _) => Set(fromSchema(schema))
      case HttpCodec.Empty                    => Set.empty
      case HttpCodec.Fallback(left, right)    =>
        fromInput(left) ++ fromInput(right)

      case HttpCodec.Halt                           => Set.empty
      case HttpCodec.Header(name, textCodec, index) => ???
      case HttpCodec.Method(codec, index)           =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method) =>
            Set(
              CliEndpoint[Unit]((_, request) => request.copy(method = method.asInstanceOf[model.Method]), Options.none),
            )
          case SimpleCodec.Unspecified()     =>
            Set(CliEndpoint[Unit]((_, request) => request, Options.none))
        }
      case HttpCodec.Path(textCodec, name, index)   => ???
      case HttpCodec.Query(name, textCodec, index)  =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.UUIDCodec        =>
            Set(
              CliEndpoint[UUID](
                (uuid, request) =>
                  request.copy(url = request.url.copy(queryParams = request.url.queryParams.add(name, uuid.toString))),
                Options
                  .text(name)
                  .mapOrFail(str =>
                    scala.util
                      .Try(UUID.fromString(str))
                      .toEither
                      .left
                      .map(error =>
                        ValidationError(
                          ValidationErrorType.InvalidValue,
                          HelpDoc.p(HelpDoc.Span.code(error.getMessage())),
                        ),
                      ),
                  ),
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) =>
                  request.copy(url = request.url.copy(queryParams = request.url.queryParams.add(name, str))),
                Options.text(name),
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) =>
                  request.copy(url = request.url.copy(queryParams = request.url.queryParams.add(name, int.toString))),
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
                (_, request) =>
                  request.copy(url = request.url.copy(queryParams = request.url.queryParams.add(name, string))),
                Options.Empty,
              ),
            )
        }
      case HttpCodec.Status(_, _)                   => Set.empty
      case HttpCodec.TransformOrFail(api, _, _)     => fromInput(api)
      case HttpCodec.WithDoc(in, _)                 => fromInput(in)
    }

  private def fromSchema[A](schema: zio.schema.Schema[A]): CliEndpoint[_] = {
    def sparseJson(prefix: List[String], json: Json): Json =
      prefix match {
        case Nil          => json
        case head :: tail => Json.Obj(Chunk(head -> sparseJson(tail, json)))
      }

    def loop(prefix: List[String], schema: zio.schema.Schema[A]): CliEndpoint[_] =
      schema match {
        case record: Schema.Record[_]                                                  => ???
        case enumeration: Schema.Enum[_]                                               => ???
        case Schema.Primitive(standardType, annotations)                               =>
          standardType match {
            case StandardType.InstantType        =>
              CliEndpoint[java.time.Instant](
                (instant, request) =>
                  request.copy(body = request.body.merge(sparseJson(prefix, Json.Str(instant.toString())))),
                Options.instant(prefix.mkString(".")),
              ) // FIXME
            case StandardType.UnitType           => ???
            case StandardType.PeriodType         => ???
            case StandardType.LongType           => ???
            case StandardType.StringType         => ???
            case StandardType.UUIDType           => ???
            case StandardType.ByteType           => ???
            case StandardType.OffsetDateTimeType => ???
            case StandardType.LocalDateType      => ???
            case StandardType.OffsetTimeType     => ???
            case StandardType.FloatType          => ???
            case StandardType.BigDecimalType     => ???
            case StandardType.BigIntegerType     => ???
            case StandardType.DoubleType         => ???
            case StandardType.BoolType           => ???
            case StandardType.CharType           => ???
            case StandardType.ZoneOffsetType     => ???
            case StandardType.YearMonthType      => ???
            case StandardType.BinaryType         => ???
            case StandardType.LocalTimeType      => ???
            case StandardType.ZoneIdType         => ???
            case StandardType.ZonedDateTimeType  => ???
            case StandardType.DayOfWeekType      => ???
            case StandardType.DurationType       => ???
            case StandardType.IntType            => ???
            case StandardType.MonthDayType       => ???
            case StandardType.ShortType          => ???
            case StandardType.LocalDateTimeType  => ???
            case StandardType.MonthType          => ???
            case StandardType.YearType           => ???
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
