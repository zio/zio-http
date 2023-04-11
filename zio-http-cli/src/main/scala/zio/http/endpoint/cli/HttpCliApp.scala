package zio.http.endpoint.cli

import scala.util.Try

import zio._
import zio.cli._
import zio.json.ast._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import scala.annotation.StaticAnnotation
import scala.collection.immutable
import zio.ZNothing
import zio.http.Client

class description(val text: String) extends StaticAnnotation

object HttpCliApp {
  private[cli] final case class CliRequest(
    url: URL,
    method: model.Method,
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

    def addPathParam(value: String) =
      self.copy(url = self.url.copy(path = self.url.path / value))

    def addQueryParam(key: String, value: String) =
      self.copy(url = self.url.withQueryParams(self.url.queryParams.add(key, value)))

    def withMethod(method: model.Method): CliRequest =
      self.copy(method = method)
  }
  private[cli] object CliRequest  {
    val empty = CliRequest(URL.empty, model.Method.GET, model.Headers.empty, Json.Obj(Chunk.empty))
  }

  private[cli] final case class CliEndpoint[A](
    embed: (A, CliRequest) => CliRequest,
    options: Options[A],
    commandNameSegments: List[Either[model.Method, String]],
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
          case Right(pathSegment)      => pathSegment
          case Left(model.Method.POST) => "create"
          case Left(model.Method.PUT)  => "update"
          case Left(method)            => method.name.toLowerCase
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
    def fromEndpoint[In, Err, Out, M <: EndpointMiddleware](endpoint: Endpoint[In, Err, Out, M]): Set[CliEndpoint[_]] =
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

        case HttpCodec.Content(schema, _)             => fromSchema(schema)
        case HttpCodec.ContentStream(schema, _)       => fromSchema(schema)
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
                  (_, request) => request.withMethod(method.asInstanceOf[model.Method]),
                  Options.none,
                  List(Left(method.asInstanceOf[model.Method])),
                  Doc.empty,
                ),
              )
            case SimpleCodec.Unspecified()     =>
              Set.empty
          }
        case HttpCodec.Path(textCodec, Some(name), _) =>
          textCodec.asInstanceOf[TextCodec[_]] match {
            case TextCodec.UUIDCodec        =>
              Set(
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
            case TextCodec.StringCodec      =>
              Set(
                CliEndpoint[String](
                  (str, request) => request.addPathParam(str),
                  Options.text(name),
                  List.empty,
                  Doc.empty,
                ),
              )
            case TextCodec.IntCodec         =>
              Set(
                CliEndpoint[BigInt](
                  (int, request) => request.addPathParam(int.toString),
                  Options.integer(name),
                  List.empty,
                  Doc.empty,
                ),
              )
            case TextCodec.BooleanCodec     =>
              Set(
                CliEndpoint[Boolean](
                  (bool, request) => request.addPathParam(bool.toString),
                  Options.boolean(name),
                  List.empty,
                  Doc.empty,
                ),
              )
            case TextCodec.Constant(string) =>
              Set(
                CliEndpoint[Unit](
                  (_, request) => request.addPathParam(string),
                  Options.Empty,
                  List(Right(string)),
                  Doc.empty,
                ),
              )
          }
        case HttpCodec.Path(textCodec, None, _)       =>
          textCodec.asInstanceOf[TextCodec[_]] match {
            case TextCodec.Constant(string) =>
              Set(
                CliEndpoint[Unit](
                  (_, request) => request.addPathParam(string),
                  Options.Empty,
                  List(Right(string)),
                  Doc.empty,
                ),
              )
            case _                          => Set.empty
          }
        case HttpCodec.Query(name, textCodec, _)      =>
          textCodec.asInstanceOf[TextCodec[_]] match {
            case TextCodec.UUIDCodec        =>
              Set(
                CliEndpoint[java.util.UUID](
                  (uuid, request) => request.addQueryParam(name, uuid.toString),
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
                  (str, request) => request.addQueryParam(name, str),
                  Options.text(name),
                  List.empty,
                  Doc.empty,
                ),
              )
            case TextCodec.IntCodec         =>
              Set(
                CliEndpoint[BigInt](
                  (int, request) => request.addQueryParam(name, int.toString),
                  Options.integer(name),
                  List.empty,
                  Doc.empty,
                ),
              )
            case TextCodec.BooleanCodec     =>
              Set(
                CliEndpoint[Boolean](
                  (bool, request) => request.addQueryParam(name, bool.toString),
                  Options.boolean(name),
                  List.empty,
                  Doc.empty,
                ),
              )
            case TextCodec.Constant(string) =>
              Set(
                CliEndpoint[Unit](
                  (_, request) => request.addQueryParam(name, string),
                  Options.Empty,
                  List.empty,
                  Doc.empty,
                ),
              )
          }
        case HttpCodec.Status(_, _)                   => Set.empty
        case HttpCodec.TransformOrFail(api, _, _)     => fromInput(api)
        case HttpCodec.WithDoc(in, doc)               => fromInput(in).map(_ describeOptions doc.toPlaintext())
      }

    private def fromSchema[A](schema: zio.schema.Schema[A]): Set[CliEndpoint[_]] = {
      def loop(prefix: List[String], schema: zio.schema.Schema[_]): Set[CliEndpoint[_]] =
        schema match {
          case record: Schema.Record[A]             =>
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
          case enumeration: Schema.Enum[A]          =>
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
                    ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(long))), // FIXME
                    Options.integer(prefix.mkString(".")),                           // FIXME
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
                    (byte, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(byte))), // FIXME
                    Options.integer(prefix.mkString(".")),                                         // FIXME
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
                    (float, request) => request.addFieldToBody(prefix, Json.Num(float)), // FIXME
                    Options.decimal(prefix.mkString(".")),                               // FIXME
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
                    (bigInt, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(bigInt))), // FIXME
                    Options.integer(prefix.mkString(".")),                                             // FIXME
                    List.empty,
                    Doc.empty,
                  ),
                )
              case StandardType.DoubleType         =>
                Set(
                  CliEndpoint[BigDecimal](
                    (double, request) => request.addFieldToBody(prefix, Json.Num(double)), // FIXME
                    Options.decimal(prefix.mkString(".")),                                 // FIXME
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
                    (char, request) => request.addFieldToBody(prefix, Json.Str(char.toString())), // FIXME
                    Options.text(prefix.mkString(".")),                                           // FIXME
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
              case StandardType.BinaryType         => ??? // TODO
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
                    ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(dayOfWeek))), // FIXME
                    Options.integer(prefix.mkString(".")),                                // FIXME
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
                    ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(duration))), // FIXME
                    Options.integer(prefix.mkString(".")),                               // FIXME
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
                    ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(short))), // FIXME
                    Options.integer(prefix.mkString(".")),                            // FIXME
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
                    ) => request.addFieldToBody(prefix, Json.Str(month)), // FIXME
                    Options.text(prefix.mkString(".")),                   // FIXME
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
                    ) => request.addFieldToBody(prefix, Json.Num(BigDecimal(year))), // FIXME
                    Options.integer(prefix.mkString(".")),                           // FIXME
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

  /**
   * Generates a [[zio.cli.CliApp]] from the given endpoints.
   *
   * @param name
   *   The name of the generated CLI
   * @param version
   *   The version of the generated CLI
   * @param summary
   *   The summary of the generated CLI
   * @param footer
   *   Footer for the help docs of the generated CLI
   * @param endpoints
   *   Chunk of [[Endpoint]]
   * @param host
   *   The server host where the generated CLI will send requests to
   * @param port
   *   The server port where the generated CLI will send requests to
   * @return
   *   a [[zio.cli.CliApp]]
   */
  def fromEndpoints[M <: EndpointMiddleware](
    name: String,
    version: String,
    summary: String,
    footer: String,
    endpoints: Chunk[Endpoint[_, _, _, M]],
    host: String,
    port: Int,
  ): CliApp[Any, Throwable, CliRequest] = {
    val cliEndpoints = endpoints.flatMap(CliEndpoint.fromEndpoint(_))

    val subcommand = cliEndpoints
      .groupBy(_.commandName)
      .map { case (name, cliEndpoints) =>
        val doc     = cliEndpoints.map(_.doc).map(_.toPlaintext()).mkString("\n\n")
        val options =
          cliEndpoints
            .map(_.options)
            .zipWithIndex
            .map { case (options, index) => options.map(index -> _) }
            .reduceOption(_ orElse _)
            .getOrElse(Options.none.map(_ => (-1, CliRequest.empty)))

        Command(name, options).withHelp(doc).map { case (index, any) =>
          val cliEndpoint = cliEndpoints(index)
          cliEndpoint
            .asInstanceOf[CliEndpoint[cliEndpoint.Type]]
            .embed(any.asInstanceOf[cliEndpoint.Type], CliRequest.empty)
        }
      }
      .reduceOption(_ orElse _)

    val command =
      subcommand match {
        case Some(subcommand) => Command(name).subcommands(subcommand)
        case None             => Command(name).map(_ => CliRequest.empty)
      }

    CliApp.make(
      name = name,
      version = version,
      summary = HelpDoc.Span.text(summary),
      footer = HelpDoc.p(footer),
      command = command,
    ) { case CliRequest(url, method, headers, body) =>
      for {
        response <- Client
          .request(
            Request
              .default(
                method,
                url.withHost(host).withPort(port),
                Body.fromString(body.toString),
              )
              .setHeaders(headers),
          )
          .provide(Client.default)
        _        <- Console.printLine(s"Got response")
        _        <- Console.printLine(s"Status: ${response.status}")
        body     <- response.body.asString
        _        <- Console.printLine(s"""Body: ${if (body.nonEmpty) body else "<empty>"}""")
      } yield ()
    }
  }
}
