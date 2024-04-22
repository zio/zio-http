/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.codec

import java.util.concurrent.ConcurrentHashMap

import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.reflect.ClassTag

import zio._

import zio.stream.ZStream

import zio.schema.Schema

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec.HttpCodec.{Annotated, Metadata}
import zio.http.codec.internal.EncoderDecoder

/**
 * A [[zio.http.codec.HttpCodec]] represents a codec for a part of an HTTP
 * request. HttpCodec the HTTP protocol, these parts may be the unconsumed
 * portion of the HTTP path (a route codec), the query string parameters (a
 * query codec), the request headers (a header codec), or the request body (a
 * body codec).
 *
 * A HttpCodec is a purely declarative description of an input, and therefore,
 * it can be used to generate documentation, clients, and client libraries.
 *
 * HttpCodecs are a bit like invertible multi-channel parsers.
 */
sealed trait HttpCodec[-AtomTypes, Value] {
  self =>

  private lazy val encoderDecoder: EncoderDecoder[AtomTypes, Value] = EncoderDecoder(self)

  /**
   * Returns a new codec that is the same as this one, but has attached docs,
   * which will render whenever docs are generated from the codec.
   */
  final def ??(doc: Doc): HttpCodec[AtomTypes, Value] =
    HttpCodec.Annotated(self, Metadata.Documented(doc))

  final def |[AtomTypes1 <: AtomTypes, Value2](
    that: HttpCodec[AtomTypes1, Value2],
  )(implicit alternator: Alternator[Value, Value2]): HttpCodec[AtomTypes1, alternator.Out] = {
    if (self eq HttpCodec.Halt) that.asInstanceOf[HttpCodec[AtomTypes1, alternator.Out]]
    else {
      HttpCodec
        .Fallback(self, that, alternator, HttpCodec.Fallback.Condition.IsHttpCodecError)
        .transform[alternator.Out](either => either.fold(alternator.left(_), alternator.right(_)))(value =>
          alternator
            .unleft(value)
            .map(Left(_))
            .orElse(alternator.unright(value).map(Right(_)))
            .get, // TODO: Solve with partiality
        )
    }
  }

  /**
   * Returns a new codec that is the composition of this codec and the specified
   * codec. For codecs that include route codecs, the routes will be decoded
   * sequentially from left to right.
   */
  final def ++[AtomTypes1 <: AtomTypes, Value2](that: HttpCodec[AtomTypes1, Value2])(implicit
    combiner: Combiner[Value, Value2],
  ): HttpCodec[AtomTypes1, combiner.Out] =
    HttpCodec.Combine[AtomTypes1, AtomTypes1, Value, Value2, combiner.Out](self, that, combiner)

  /**
   * To end the route codec and begin with query codec, and re-interprets as
   * PathQueryCodec
   *
   * GET /ab/c :? paramStr("") &
   */
  final def ^?[Value2](
    that: QueryCodec[Value2],
  )(implicit
    combiner: Combiner[Value, Value2],
    ev: HttpCodecType.Path <:< AtomTypes,
  ): HttpCodec[HttpCodecType.PathQuery, combiner.Out] =
    (self ++ that).asInstanceOf[HttpCodec[HttpCodecType.PathQuery, combiner.Out]]

  /**
   * Append more query parameters to either a query codec, or to a pathQuery
   * codec which is a combination of path and query
   */
  final def &[Value2](
    that: QueryCodec[Value2],
  )(implicit
    combiner: Combiner[Value, Value2],
    ev: HttpCodecType.Query <:< AtomTypes,
  ): HttpCodec[HttpCodecType.Query, combiner.Out] =
    self.asQuery ++ that

  /**
   * Produces a flattened collection of alternatives. Once flattened, each codec
   * inside the returned collection is guaranteed to contain no nested
   * alternatives.
   */
  final def alternatives: Chunk[(HttpCodec[AtomTypes, Value], HttpCodec.Fallback.Condition)] =
    HttpCodec.flattenFallbacks(self)

  /**
   * Returns a new codec that is the same as this one, but has attached
   * metadata, such as docs.
   */
  def annotate(metadata: Metadata[Value]): HttpCodec[AtomTypes, Value] =
    HttpCodec.Annotated(self, metadata)

  /**
   * Reinterprets this codec as a query codec assuming evidence that this
   * interpretation is sound.
   */
  final def asQuery(implicit ev: HttpCodecType.Query <:< AtomTypes): QueryCodec[Value] =
    self.asInstanceOf[QueryCodec[Value]]

  /**
   * Transforms the type parameter to `Unit` by specifying that all possible
   * values that can be decoded from this `HttpCodec` are in fact equivalent to
   * the provided canonical value.
   *
   * Note: You should NOT use this method on any codec which can decode
   * semantically distinct values.
   */
  final def const(canonical: => Value): HttpCodec[AtomTypes, Unit] =
    self.transform(_ => ())(_ => canonical)

  final def const[Value2](value2: => Value2)(implicit ev: Unit <:< Value): HttpCodec[AtomTypes, Value2] =
    self.transform(_ => value2)(_ => ev(()))

  /**
   * Uses this codec to decode the Scala value from a request.
   */
  final def decodeRequest(request: Request)(implicit trace: Trace): Task[Value] =
    decode(request.url, Status.Ok, request.method, request.headers, request.body)

  /**
   * Uses this codec to decode the Scala value from a response.
   */
  final def decodeResponse(response: Response)(implicit trace: Trace): Task[Value] =
    decode(URL.empty, response.status, Method.GET, response.headers, response.body)

  private final def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value] =
    encoderDecoder.decode(url, status, method, headers, body)

  def doc: Option[Doc] = {
    @tailrec
    def loop(codec: HttpCodec[_, _]): Option[Doc] =
      codec match {
        case Annotated(_, Metadata.Documented(doc)) => Some(doc)
        case Annotated(codec, _)                    => loop(codec)
        case _                                      => None
      }

    loop(self)
  }

  /**
   * Uses this codec to encode the Scala value into a request.
   */
  final def encodeRequest(value: Value): Request =
    encodeWith(value, Chunk.empty)((url, _, method, headers, body) =>
      Request(
        url = url,
        method = method.getOrElse(Method.GET),
        headers = headers,
        body = body,
        version = Version.Default,
        remoteAddress = None,
      ),
    )

  /**
   * Uses this codec to encode the Scala value as a patch to a request.
   */
  final def encodeRequestPatch(value: Value): Request.Patch =
    encodeWith(value, Chunk.empty)((url, _, _, headers, _) =>
      Request.Patch(
        addQueryParams = url.queryParams,
        addHeaders = headers,
      ),
    )

  /**
   * Uses this codec to encode the Scala value as a response.
   */
  final def encodeResponse[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor]): Response =
    encodeWith(value, outputTypes)((_, status, _, headers, body) =>
      Response(headers = headers, body = body, status = status.getOrElse(Status.Ok)),
    )

  /**
   * Uses this codec to encode the Scala value as a response patch.
   */
  final def encodeResponsePatch[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor]): Response.Patch =
    encodeWith(value, outputTypes)((_, status, _, headers, _) =>
      Response.Patch.addHeaders(headers) ++ status.map(Response.Patch.status(_)).getOrElse(Response.Patch.empty),
    )

  private final def encodeWith[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
    f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
  ): Z =
    encoderDecoder.encodeWith(value, outputTypes.sortBy(mt => -mt.qFactor.getOrElse(1.0)))(f)

  def examples(examples: Iterable[(String, Value)]): HttpCodec[AtomTypes, Value] =
    HttpCodec.Annotated(self, Metadata.Examples(Chunk.fromIterable(examples).toMap))

  def examples(example1: (String, Value), examples: (String, Value)*): HttpCodec[AtomTypes, Value] =
    HttpCodec.Annotated(self, Metadata.Examples((example1 +: Chunk.fromIterable(examples)).toMap))

  def examples: Map[String, Value] = Map.empty

  /**
   * Returns a new codec that will expect the value to be equal to the specified
   * value.
   */
  def expect(expected: Value): HttpCodec[AtomTypes, Unit] =
    transformOrFailLeft(actual =>
      if (actual == expected) Right(())
      else Left(s"Expected ${expected} but found ${actual}"),
    )(_ => expected)

  def named(name: String): HttpCodec[AtomTypes, Value] =
    HttpCodec.Annotated(self, Metadata.Named(name))

  /**
   * Returns a new codec that is the same as this one, but has attached a name.
   * This name is used for documentation generation.
   */
  def named(named: Metadata.Named[Value]): HttpCodec[AtomTypes, Value] =
    HttpCodec.Annotated(self, Metadata.Named(named.name))

  /**
   * Returns a new codec, where the value produced by this one is optional.
   */
  final def optional: HttpCodec[AtomTypes, Option[Value]] =
    Annotated(
      if (self eq HttpCodec.Halt) HttpCodec.empty.asInstanceOf[HttpCodec[AtomTypes, Option[Value]]]
      else {
        HttpCodec
          .Fallback(self, HttpCodec.empty, Alternator.either, HttpCodec.Fallback.Condition.isMissingDataOnly)
          .transform[Option[Value]](either => either.fold(Some(_), _ => None))(_.toLeft(()))
      },
      Metadata.Optional(),
    )

  final def orElseEither[AtomTypes1 <: AtomTypes, Value2](
    that: HttpCodec[AtomTypes1, Value2],
  )(implicit alternator: Alternator[Value, Value2]): HttpCodec[AtomTypes1, alternator.Out] =
    self | that

  final def toLeft[R]: HttpCodec[AtomTypes, Either[Value, R]] =
    self.transformOrFail[Either[Value, R]](value => Right(Left(value)))(either =>
      either.swap.left.map(_ => "Error!"),
    ) // TODO: Solve with partiality

  final def toRight[L]: HttpCodec[AtomTypes, Either[L, Value]] =
    self.transformOrFail[Either[L, Value]](value => Right(Right(value)))(either =>
      either.left.map(_ => "Error!"),
    ) // TODO: Solve with partiality

  /**
   * Transforms the type parameter of this HttpCodec from `Value` to `Value2`.
   * Due to the fact that HttpCodec is invariant in its type parameter, the
   * transformation requires not just a function from `Value` to `Value2`, but
   * also, a function from `Value2` to `Value`.
   *
   * One of these functions will be used in decoding, for example, when the
   * endpoint is invoked on the server. The other of these functions will be
   * used in encoding, for example, when a client calls the endpoint on the
   * server.
   */
  final def transform[Value2](f: Value => Value2)(g: Value2 => Value): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, in => Right(f(in)), output => Right(g(output)))

  final def transformOrFail[Value2](f: Value => Either[String, Value2])(
    g: Value2 => Either[String, Value],
  ): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, f, g)

  final def transformOrFailLeft[Value2](f: Value => Either[String, Value2])(
    g: Value2 => Value,
  ): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, f, output => Right(g(output)))

  final def transformOrFailRight[Value2](f: Value => Value2)(
    g: Value2 => Either[String, Value],
  ): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, in => Right(f(in)), g)
}

object HttpCodec extends ContentCodecs with HeaderCodecs with MethodCodecs with QueryCodecs with StatusCodecs {
  def enumeration[Value]: Enumeration[Value] =
    new Enumeration[Value](())

  def error[Body](status: zio.http.Status)(implicit
    schema: Schema[Body],
  ): HttpCodec[HttpCodecType.Status with HttpCodecType.Content, Body] =
    content[Body]("error-response") ++ this.status(status)

  private[http] sealed trait AtomTag
  private[http] object AtomTag {
    case object Status  extends AtomTag
    case object Path    extends AtomTag
    case object Content extends AtomTag
    case object Query   extends AtomTag
    case object Header  extends AtomTag
    case object Method  extends AtomTag
  }

  def empty: HttpCodec[Any, Unit] =
    Empty

  def unused: HttpCodec[Any, ZNothing] = Halt

  final case class Enumeration[Value](unit: Unit) extends AnyVal {
    def f2[AtomTypes, Sub1 <: Value: ClassTag, Sub2 <: Value: ClassTag](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2).transformOrFail(either => Right(either.merge))((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(sub1))
          case sub2: Sub2 => Right(Right(sub2))
          case _          => Left(s"Unexpected error type")
        },
      )

    def f3[AtomTypes, Sub1 <: Value: ClassTag, Sub2 <: Value: ClassTag, Sub3 <: Value: ClassTag](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
      codec3: HttpCodec[AtomTypes, Sub3],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2 | codec3).transformOrFail(either => Right(either.left.map(_.merge).merge))((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(Left(sub1)))
          case sub2: Sub2 => Right(Left(Right(sub2)))
          case sub3: Sub3 => Right(Right(sub3))
          case _          => Left(s"Unexpected error type")
        },
      )

    def f4[
      AtomTypes,
      Sub1 <: Value: ClassTag,
      Sub2 <: Value: ClassTag,
      Sub3 <: Value: ClassTag,
      Sub4 <: Value: ClassTag,
    ](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
      codec3: HttpCodec[AtomTypes, Sub3],
      codec4: HttpCodec[AtomTypes, Sub4],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2 | codec3 | codec4).transformOrFail(either =>
        Right(either.left.map(_.left.map(_.merge).merge).merge),
      )((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(Left(Left(sub1))))
          case sub2: Sub2 => Right(Left(Left(Right(sub2))))
          case sub3: Sub3 => Right(Left(Right(sub3)))
          case sub4: Sub4 => Right(Right(sub4))
          case _          => Left(s"Unexpected error type")
        },
      )

    def f5[
      AtomTypes,
      Sub1 <: Value: ClassTag,
      Sub2 <: Value: ClassTag,
      Sub3 <: Value: ClassTag,
      Sub4 <: Value: ClassTag,
      Sub5 <: Value: ClassTag,
    ](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
      codec3: HttpCodec[AtomTypes, Sub3],
      codec4: HttpCodec[AtomTypes, Sub4],
      codec5: HttpCodec[AtomTypes, Sub5],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2 | codec3 | codec4 | codec5).transformOrFail(either =>
        Right(either.left.map(_.left.map(_.left.map(_.merge).merge).merge).merge),
      )((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(Left(Left(Left(sub1)))))
          case sub2: Sub2 => Right(Left(Left(Left(Right(sub2)))))
          case sub3: Sub3 => Right(Left(Left(Right(sub3))))
          case sub4: Sub4 => Right(Left(Right(sub4)))
          case sub5: Sub5 => Right(Right(sub5))
          case _          => Left(s"Unexpected error type")
        },
      )

    def f6[
      AtomTypes,
      Sub1 <: Value: ClassTag,
      Sub2 <: Value: ClassTag,
      Sub3 <: Value: ClassTag,
      Sub4 <: Value: ClassTag,
      Sub5 <: Value: ClassTag,
      Sub6 <: Value: ClassTag,
    ](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
      codec3: HttpCodec[AtomTypes, Sub3],
      codec4: HttpCodec[AtomTypes, Sub4],
      codec5: HttpCodec[AtomTypes, Sub5],
      codec6: HttpCodec[AtomTypes, Sub6],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2 | codec3 | codec4 | codec5 | codec6).transformOrFail(either =>
        Right(either.left.map(_.left.map(_.left.map(_.left.map(_.merge).merge).merge).merge).merge),
      )((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(Left(Left(Left(Left(sub1))))))
          case sub2: Sub2 => Right(Left(Left(Left(Left(Right(sub2))))))
          case sub3: Sub3 => Right(Left(Left(Left(Right(sub3)))))
          case sub4: Sub4 => Right(Left(Left(Right(sub4))))
          case sub5: Sub5 => Right(Left(Right(sub5)))
          case sub6: Sub6 => Right(Right(sub6))
          case _          => Left(s"Unexpected error type")
        },
      )

    def f7[
      AtomTypes,
      Sub1 <: Value: ClassTag,
      Sub2 <: Value: ClassTag,
      Sub3 <: Value: ClassTag,
      Sub4 <: Value: ClassTag,
      Sub5 <: Value: ClassTag,
      Sub6 <: Value: ClassTag,
      Sub7 <: Value: ClassTag,
    ](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
      codec3: HttpCodec[AtomTypes, Sub3],
      codec4: HttpCodec[AtomTypes, Sub4],
      codec5: HttpCodec[AtomTypes, Sub5],
      codec6: HttpCodec[AtomTypes, Sub6],
      codec7: HttpCodec[AtomTypes, Sub7],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2 | codec3 | codec4 | codec5 | codec6 | codec7).transformOrFail(either =>
        Right(either.left.map(_.left.map(_.left.map(_.left.map(_.left.map(_.merge).merge).merge).merge).merge).merge),
      )((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(Left(Left(Left(Left(Left(sub1)))))))
          case sub2: Sub2 => Right(Left(Left(Left(Left(Left(Right(sub2)))))))
          case sub3: Sub3 => Right(Left(Left(Left(Left(Right(sub3))))))
          case sub4: Sub4 => Right(Left(Left(Left(Right(sub4)))))
          case sub5: Sub5 => Right(Left(Left(Right(sub5))))
          case sub6: Sub6 => Right(Left(Right(sub6)))
          case sub7: Sub7 => Right(Right(sub7))
          case _          => Left(s"Unexpected error type")
        },
      )

    def f8[
      AtomTypes,
      Sub1 <: Value: ClassTag,
      Sub2 <: Value: ClassTag,
      Sub3 <: Value: ClassTag,
      Sub4 <: Value: ClassTag,
      Sub5 <: Value: ClassTag,
      Sub6 <: Value: ClassTag,
      Sub7 <: Value: ClassTag,
      Sub8 <: Value: ClassTag,
    ](
      codec1: HttpCodec[AtomTypes, Sub1],
      codec2: HttpCodec[AtomTypes, Sub2],
      codec3: HttpCodec[AtomTypes, Sub3],
      codec4: HttpCodec[AtomTypes, Sub4],
      codec5: HttpCodec[AtomTypes, Sub5],
      codec6: HttpCodec[AtomTypes, Sub6],
      codec7: HttpCodec[AtomTypes, Sub7],
      codec8: HttpCodec[AtomTypes, Sub8],
    ): HttpCodec[AtomTypes, Value] =
      (codec1 | codec2 | codec3 | codec4 | codec5 | codec6 | codec7 | codec8).transformOrFail(either =>
        Right(
          either.left
            .map(_.left.map(_.left.map(_.left.map(_.left.map(_.left.map(_.merge).merge).merge).merge).merge).merge)
            .merge,
        ),
      )((value: Value) =>
        value match {
          case sub1: Sub1 => Right(Left(Left(Left(Left(Left(Left(Left(sub1))))))))
          case sub2: Sub2 => Right(Left(Left(Left(Left(Left(Left(Right(sub2))))))))
          case sub3: Sub3 => Right(Left(Left(Left(Left(Left(Right(sub3)))))))
          case sub4: Sub4 => Right(Left(Left(Left(Left(Right(sub4))))))
          case sub5: Sub5 => Right(Left(Left(Left(Right(sub5)))))
          case sub6: Sub6 => Right(Left(Left(Right(sub6))))
          case sub7: Sub7 => Right(Left(Right(sub7)))
          case sub8: Sub8 => Right(Right(sub8))
          case _          => Left(s"Unexpected error type")
        },
      )
  }

  private[http] sealed trait Atom[-AtomTypes, Value0] extends HttpCodec[AtomTypes, Value0] {
    def tag: AtomTag

    def index: Int

    def index(index: Int): Atom[AtomTypes, Value0]
  }

  private[http] final case class Status[A](codec: SimpleCodec[zio.http.Status, A], index: Int = 0)
      extends Atom[HttpCodecType.Status, A] {
    self =>
    def erase: Status[Any] = self.asInstanceOf[Status[Any]]

    def tag: AtomTag = AtomTag.Status

    def index(index: Int): Status[A] = copy(index = index)
  }
  private[http] final case class Path[A](pathCodec: PathCodec[A], index: Int = 0) extends Atom[HttpCodecType.Path, A] {
    self =>
    def erase: Path[Any] = self.asInstanceOf[Path[Any]]

    def tag: AtomTag = AtomTag.Path

    def index(index: Int): Path[A] = copy(index = index)
  }
  private[http] final case class Content[A](
    codec: HttpContentCodec[A],
    name: Option[String],
    index: Int = 0,
  ) extends Atom[HttpCodecType.Content, A] {
    self =>
    def tag: AtomTag = AtomTag.Content

    def index(index: Int): Content[A] = copy(index = index)
  }
  private[http] final case class ContentStream[A](
    codec: HttpContentCodec[A],
    name: Option[String],
    index: Int = 0,
  ) extends Atom[HttpCodecType.Content, ZStream[Any, Nothing, A]] {
    def tag: AtomTag = AtomTag.Content

    def index(index: Int): ContentStream[A] = copy(index = index)
  }
  private[http] final case class Query[A](
    name: String,
    textCodec: TextCodec[A],
    hint: Query.QueryParamHint,
    index: Int = 0,
  ) extends Atom[HttpCodecType.Query, Chunk[A]] {
    self =>
    def erase: Query[Any] = self.asInstanceOf[Query[Any]]

    def tag: AtomTag = AtomTag.Query

    def index(index: Int): Query[A] = copy(index = index)
  }

  private[http] object Query {

    // Hint on how many query parameters codec expects
    sealed trait QueryParamHint
    object QueryParamHint {
      case object One extends QueryParamHint

      case object Many extends QueryParamHint

      case object Zero extends QueryParamHint

      case object Any extends QueryParamHint
    }
  }

  private[http] final case class Method[A](codec: SimpleCodec[zio.http.Method, A], index: Int = 0)
      extends Atom[HttpCodecType.Method, A] {
    self =>
    def erase: Method[Any] = self.asInstanceOf[Method[Any]]
    def tag: AtomTag       = AtomTag.Method

    def index(index: Int): Method[A] = copy(index = index)
  }

  private[http] final case class Header[A](name: String, textCodec: TextCodec[A], index: Int = 0)
      extends Atom[HttpCodecType.Header, A] {
    self =>
    def erase: Header[Any] = self.asInstanceOf[Header[Any]]

    def tag: AtomTag = AtomTag.Header

    def index(index: Int): Header[A] = copy(index = index)
  }

  private[http] final case class Annotated[AtomTypes, Value](
    codec: HttpCodec[AtomTypes, Value],
    metadata: Metadata[Value],
  ) extends HttpCodec[AtomTypes, Value] {
    override def examples: Map[String, Value] =
      metadata match {
        case value: Metadata.Examples[Value] =>
          value.examples ++ codec.examples
        case _                               =>
          codec.examples
      }
  }

  sealed trait Metadata[Value] {
    def transform[Value2](f: Value => Value2): Metadata[Value2] =
      this match {
        case Metadata.Named(name)     => Metadata.Named(name)
        case Metadata.Optional()      => Metadata.Optional()
        case Metadata.Examples(ex)    => Metadata.Examples(ex.map { case (k, v) => k -> f(v) })
        case Metadata.Documented(doc) => Metadata.Documented(doc)
        case Metadata.Deprecated(doc) => Metadata.Deprecated(doc)
      }
  }

  object Metadata {
    final case class Named[A](name: String) extends Metadata[A]

    final case class Optional[A]() extends Metadata[A]

    final case class Examples[A](examples: Map[String, A]) extends Metadata[A]

    final case class Documented[A](doc: Doc) extends Metadata[A]

    final case class Deprecated[A](doc: Doc) extends Metadata[A]
  }

  private[http] final case class TransformOrFail[AtomType, X, A](
    api: HttpCodec[AtomType, X],
    f: X => Either[String, A],
    g: A => Either[String, X],
  ) extends HttpCodec[AtomType, A] {
    type In  = X
    type Out = A
  }

  private[http] case object Empty extends HttpCodec[Any, Unit]

  private[http] case object Halt extends HttpCodec[Any, Nothing]

  private[http] final case class Combine[AtomType1, AtomType2, A1, A2, A](
    left: HttpCodec[AtomType1, A1],
    right: HttpCodec[AtomType2, A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends HttpCodec[AtomType1 with AtomType2, A] {
    type Left  = A1
    type Right = A2
    type Out   = A
  }

  private[http] final case class Fallback[AtomType, A, B](
    left: HttpCodec[AtomType, A],
    right: HttpCodec[AtomType, B],
    alternator: Alternator[A, B],
    condition: Fallback.Condition,
  ) extends HttpCodec[AtomType, Either[A, B]] {
    type Left  = A
    type Right = B
    type Out   = Either[A, B]
  }

  private[http] object Fallback {

    /**
     * `Condition` describes the circumstances under which the `right` codec in
     * a `Fallback` is willing to attempt to recover from a failure of the
     * `left` codec. All implementations of `Fallback` other than `optional` are
     * willing to attempt to recover from any `HttpCodecError`. Implementations
     * of `Fallback` constructed from `optional` are only willing to attempt to
     * recover from `MissingHeader` or `MissingQueryParam` errors.
     */
    sealed trait Condition { self =>
      def apply(cause: Cause[Any]): Boolean   =
        self match {
          case Condition.IsHttpCodecError  => HttpCodecError.isHttpCodecError(cause)
          case Condition.isMissingDataOnly => HttpCodecError.isMissingDataOnly(cause)
        }
      def combine(that: Condition): Condition =
        (self, that) match {
          case (Condition.isMissingDataOnly, _) => Condition.isMissingDataOnly
          case (_, Condition.isMissingDataOnly) => Condition.isMissingDataOnly
          case _                                => Condition.IsHttpCodecError
        }
      def isHttpCodecError: Boolean           = self match {
        case Condition.IsHttpCodecError => true
        case _                          => false
      }
      def isMissingDataOnly: Boolean          = self match {
        case Condition.isMissingDataOnly => true
        case _                           => false
      }
    }
    object Condition       {
      case object IsHttpCodecError  extends Condition
      case object isMissingDataOnly extends Condition
    }
  }

  private[http] def flattenFallbacks[AtomTypes, A](
    api: HttpCodec[AtomTypes, A],
  ): Chunk[(HttpCodec[AtomTypes, A], Fallback.Condition)] = {

    def rewrite[T, B](
      api: HttpCodec[T, B],
      annotations: Chunk[HttpCodec.Metadata[B]],
    ): Chunk[(HttpCodec[T, B], Fallback.Condition)] =
      api match {
        case fallback @ HttpCodec.Fallback(left, right, alternator, condition) =>
          rewrite[T, fallback.Left](left, reduceExamplesLeft(annotations, alternator)).map { case (codec, condition) =>
            codec.toLeft[fallback.Right] -> condition
          } ++
            rewrite[T, fallback.Right](right, reduceExamplesRight(annotations, alternator)).map { case (codec, _) =>
              codec.toRight[fallback.Left] -> condition
            }

        case transform @ HttpCodec.TransformOrFail(codec, f, g) =>
          rewrite[T, transform.In](
            codec,
            annotations.map(_.transform { v =>
              g(v) match {
                case Left(error)  => throw new Exception(error)
                case Right(value) => value
              }
            }),
          ).map { case (codec, condition) =>
            HttpCodec.TransformOrFail(codec, f, g) -> condition
          }

        case combine @ HttpCodec.Combine(left, right, combiner) =>
          for {
            (l, lCondition) <- rewrite[T, combine.Left](left, reduceExamplesLeft(annotations, combiner))
            (r, rCondition) <- rewrite[T, combine.Right](right, reduceExamplesRight(annotations, combiner))
          } yield HttpCodec.Combine(l, r, combiner) -> lCondition.combine(rCondition)

        case HttpCodec.Annotated(in, metadata) =>
          rewrite[T, B](in, metadata +: annotations)

        case HttpCodec.Empty => Chunk.single(HttpCodec.Empty -> Fallback.Condition.IsHttpCodecError)

        case HttpCodec.Halt => Chunk.empty

        case atom: Atom[_, _] =>
          Chunk.single(annotations.foldLeft[HttpCodec[T, B]](atom)(_ annotate _) -> Fallback.Condition.IsHttpCodecError)
      }

    rewrite(api, Chunk.empty)
  }

  private[http] def reduceExamplesLeft[T, L, R](
    annotations: Chunk[HttpCodec.Metadata[T]],
    combiner: Combiner[L, R],
  ): Chunk[HttpCodec.Metadata[L]] =
    annotations.map {
      case HttpCodec.Metadata.Examples(examples) =>
        HttpCodec.Metadata.Examples(examples.map { case (name, value) =>
          name -> combiner.separate(value.asInstanceOf[combiner.Out])._1
        })
      case other                                 =>
        other.asInstanceOf[HttpCodec.Metadata[L]]
    }

  private[http] def reduceExamplesLeft[T, L, R](
    annotations: Chunk[HttpCodec.Metadata[T]],
    alternator: Alternator[L, R],
  ): Chunk[HttpCodec.Metadata[L]] =
    annotations.map {
      case HttpCodec.Metadata.Examples(examples) =>
        HttpCodec.Metadata.Examples(examples.flatMap { case (name, value) =>
          alternator.unleft(value.asInstanceOf[alternator.Out]).map(name -> _)
        })
      case other                                 =>
        other.asInstanceOf[HttpCodec.Metadata[L]]
    }

  private[http] def reduceExamplesRight[T, L, R](
    annotations: Chunk[HttpCodec.Metadata[T]],
    combiner: Combiner[L, R],
  ): Chunk[HttpCodec.Metadata[R]] =
    annotations.map {
      case HttpCodec.Metadata.Examples(examples) =>
        HttpCodec.Metadata.Examples(examples.map { case (name, value) =>
          name -> combiner.separate(value.asInstanceOf[combiner.Out])._2
        })
      case other                                 =>
        other.asInstanceOf[HttpCodec.Metadata[R]]
    }

  private[http] def reduceExamplesRight[T, L, R](
    annotations: Chunk[HttpCodec.Metadata[T]],
    alternator: Alternator[L, R],
  ): Chunk[HttpCodec.Metadata[R]] =
    annotations.map {
      case HttpCodec.Metadata.Examples(examples) =>
        HttpCodec.Metadata.Examples(examples.flatMap { case (name, value) =>
          alternator.unright(value.asInstanceOf[alternator.Out]).map(name -> _)
        })
      case other                                 =>
        other.asInstanceOf[HttpCodec.Metadata[R]]
    }
}
