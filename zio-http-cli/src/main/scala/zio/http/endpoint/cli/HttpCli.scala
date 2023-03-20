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

class description(val text: String) extends StaticAnnotation

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
final case class CliEndpoint[A](
  embed: (A, CliRequest) => CliRequest,
  options: Options[A],
  commandNames: List[Either[model.Method, String]],
) {
  self =>
  type Type = A

  def ++[B](that: CliEndpoint[B]): CliEndpoint[(A, B)] =
    CliEndpoint(
      { case ((a, b), request) =>
        that.embed(b, self.embed(a, request))
      },
      self.options ++ that.options,
      self.commandNames ++ that.commandNames,
    )

  def ??(description: String) = self.copy(options = options ?? description)

  lazy val command: Command[A] = {
    Command(
      self.commandNames
        .sortBy(_.isRight)
        .map {
          case Right(pathSegment) => pathSegment
          case Left(method)       => method.name.toLowerCase
        }
        .mkString("-"),
      self.options,
    )
  }

  lazy val optional: CliEndpoint[Option[A]] =
    CliEndpoint(
      {
        case (Some(a), request) => self.embed(a, request)
        case (None, request)    => request
      },
      self.options.optional,
      self.commandNames,
    )

  def transform[B](f: A => B, g: B => A): CliEndpoint[B] =
    CliEndpoint(
      (b, request) => self.embed(g(b), request),
      self.options.map(f),
      self.commandNames,
    )
}
object CliEndpoint {
  def fromEndpoint[In, Err, Out, M <: EndpointMiddleware](endpoint: Endpoint[In, Err, Out, M]): Set[CliEndpoint[_]] =
    fromInput(endpoint.input)

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
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.addHeader(name, str),
                Options.text(name),
                List.empty,
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.addHeader(name, int.toString),
                Options.integer(name),
                List.empty,
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) => request.addHeader(name, bool.toString),
                Options.boolean(name),
                List.empty,
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.addHeader(name, string),
                Options.Empty,
                List.empty,
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
                (uuid, request) => request.copy(url = request.url.copy(path = request.url.path / uuid.toString)),
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
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.copy(url = request.url.copy(path = request.url.path / str)),
                Options.text(name),
                List.empty,
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.copy(url = request.url.copy(path = request.url.path / int.toString)),
                Options.integer(name),
                List.empty,
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) => request.copy(url = request.url.copy(path = request.url.path / bool.toString)),
                Options.boolean(name),
                List.empty,
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.copy(url = request.url.copy(path = request.url.path / string)),
                Options.Empty,
                List(Right(string)),
              ),
            )
        }
      case HttpCodec.Path(textCodec, None, _)       =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.copy(url = request.url.copy(path = request.url.path / string)),
                Options.Empty,
                List(Right(string)),
              ),
            )
          case _                          => Set.empty
        }
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
                List.empty,
              ),
            )
          case TextCodec.StringCodec      =>
            Set(
              CliEndpoint[String](
                (str, request) => request.addPathParam(name, str),
                Options.text(name),
                List.empty,
              ),
            )
          case TextCodec.IntCodec         =>
            Set(
              CliEndpoint[BigInt](
                (int, request) => request.addPathParam(name, int.toString),
                Options.integer(name),
                List.empty,
              ),
            )
          case TextCodec.BooleanCodec     =>
            Set(
              CliEndpoint[Boolean](
                (bool, request) => request.addPathParam(name, bool.toString),
                Options.boolean(name),
                List.empty,
              ),
            )
          case TextCodec.Constant(string) =>
            Set(
              CliEndpoint[Unit](
                (_, request) => request.addPathParam(name, string),
                Options.Empty,
                List.empty,
              ),
            )
        }
      case HttpCodec.Status(_, _)                   => Set.empty
      case HttpCodec.TransformOrFail(api, _, _)     => fromInput(api)
      case HttpCodec.WithDoc(in, doc)               => fromInput(in).map(_ ?? doc.toPlaintext())
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
                    case Some(description) => cliEndpoint ?? description.asInstanceOf[description].text
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
                ),
              )
            case StandardType.UnitType           => Set.empty
            case StandardType.PeriodType         =>
              Set(
                CliEndpoint[java.time.Period](
                  (period, request) => request.addFieldToBody(prefix, Json.Str(period.toString())),
                  Options.period(prefix.mkString(".")), // FIXME
                  List.empty,
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
                ),
              )
            case StandardType.StringType         =>
              Set(
                CliEndpoint[String](
                  (str, request) => request.addFieldToBody(prefix, Json.Str(str)),
                  Options.text(prefix.mkString(".")), // FIXME
                  List.empty,
                ),
              )
            case StandardType.UUIDType           =>
              Set(
                CliEndpoint[String](
                  (uuid, request) => request.addFieldToBody(prefix, Json.Str(uuid.toString())),
                  Options.text(prefix.mkString(".")), // FIXME
                  List.empty,
                ),
              )
            case StandardType.ByteType           =>
              Set(
                CliEndpoint[BigInt](
                  (byte, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(byte))), // FIXME
                  Options.integer(prefix.mkString(".")),                                         // FIXME
                  List.empty,
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
                ),
              )
            case StandardType.FloatType          =>
              Set(
                CliEndpoint[BigDecimal](
                  (float, request) => request.addFieldToBody(prefix, Json.Num(float)), // FIXME
                  Options.decimal(prefix.mkString(".")),                               // FIXME
                  List.empty,
                ),
              )
            case StandardType.BigDecimalType     =>
              Set(
                CliEndpoint[BigDecimal](
                  (bigDecimal, request) => request.addFieldToBody(prefix, Json.Num(bigDecimal)),
                  Options.decimal(prefix.mkString(".")), // FIXME
                  List.empty,
                ),
              )
            case StandardType.BigIntegerType     =>
              Set(
                CliEndpoint[BigInt](
                  (bigInt, request) => request.addFieldToBody(prefix, Json.Num(BigDecimal(bigInt))), // FIXME
                  Options.integer(prefix.mkString(".")),                                             // FIXME
                  List.empty,
                ),
              )
            case StandardType.DoubleType         =>
              Set(
                CliEndpoint[BigDecimal](
                  (double, request) => request.addFieldToBody(prefix, Json.Num(double)), // FIXME
                  Options.decimal(prefix.mkString(".")),                                 // FIXME
                  List.empty,
                ),
              )
            case StandardType.BoolType           =>
              Set(
                CliEndpoint[Boolean](
                  (bool, request) => request.addFieldToBody(prefix, Json.Bool(bool)),
                  Options.boolean(prefix.mkString(".")), // FIXME
                  List.empty,
                ),
              )
            case StandardType.CharType           =>
              Set(
                CliEndpoint[String](
                  (char, request) => request.addFieldToBody(prefix, Json.Str(char.toString())), // FIXME
                  Options.text(prefix.mkString(".")),                                           // FIXME
                  List.empty,
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

object Test extends scala.App {
  import HttpCodec._

  final case class User(
    @description("The unique identifier of the User")
    id: Int,
    @description("The user's name")
    name: String,
    @description("The user's email")
    email: Option[String],
  )
  object User {
    implicit val schema = DeriveSchema.gen[User]
  }
  final case class Post(
    @description("The unique identifier of the User")
    userId: Int,
    @description("The unique identifier of the Post")
    postId: Int,
    @description("The post's contents")
    contents: String,
  )
  object Post {
    implicit val schema = DeriveSchema.gen[Post]
  }

  val getUser =
    Endpoint
      .get("users" / int("userId") ?? Doc.p("The unique identifier of the user"))
      .header(HeaderCodec.location ?? Doc.p("The user's location"))
      .out[User]

  val getUserRoute =
    getUser.implement { case (id, _) =>
      ZIO.succeed(User(id, "Juanito", Some("juanito@test.com")))
    }

  val getUserPosts =
    Endpoint
      .get(
        "users" / int("userId") ?? Doc.p("The unique identifier of the user") / "posts" / int("postId") ?? Doc.p(
          "The unique identifier of the post",
        ),
      )
      .query(query("name") ?? Doc.p("The user's name"))
      .out[List[Post]]

  val getUserPostsRoute =
    getUserPosts.implement { case (userId, postId, name) =>
      ZIO.succeed(List(Post(userId, postId, name)))
    }

  val createUser =
    Endpoint
      .post("users")
      .in[User]
      .out[String]

  val createUserRoute =
    createUser.implement { user =>
      ZIO.succeed(user.name)
    }

  val routes = getUserRoute ++ getUserPostsRoute ++ createUserRoute

  val cliRequest =
    CliRequest(
      URL.fromString("http://test.com").right.get,
      model.Method.GET,
      QueryParams.empty,
      model.Headers.empty,
      Json.Null,
    )

  val cliEndpoints1 = CliEndpoint.fromEndpoint(getUser).asInstanceOf[Set[CliEndpoint[(((Unit, BigInt), Unit), String)]]]
  println(cliEndpoints1.map(_.command.names).head.head)
  println(cliEndpoints1.map(_.command.helpDoc.toPlaintext()).head)
  println(cliEndpoints1.map(_.embed(((((), 1000), ()), "test-location"), cliRequest)).head)

  val cliEndpoints2 = CliEndpoint.fromEndpoint(getUserPosts)
  println(cliEndpoints2.map(_.command.names).head.head)
  println(cliEndpoints2.map(_.command.helpDoc.toPlaintext()).head)

  val cliEndpoints3 = CliEndpoint.fromEndpoint(createUser)
  println(cliEndpoints3.map(_.command.names).head.head)
  println(cliEndpoints3.map(_.command.helpDoc.toPlaintext()).head)
}
