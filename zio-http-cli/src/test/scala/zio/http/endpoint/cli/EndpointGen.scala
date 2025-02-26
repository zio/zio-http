package zio.http.endpoint.cli

import zio.ZNothing
import zio.test._

import zio.schema.Schema

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.cli.AuxGen._
import zio.http.endpoint.cli.CliRepr.CliReprOf

/**
 * Constructs a Gen[Any, CliRepr[CliEndpoint, CliEndpoint]]
 */

object EndpointGen {

  type CodecType = HttpCodecType.RequestType

  type Codec[A] = HttpCodec[CodecType, A]

  def fromInputCodec[Input](
    doc: Doc,
    input: HttpCodec[CodecType, Input],
  ): Endpoint[Path, Input, ZNothing, ZNothing, AuthType.None] =
    Endpoint(
      RoutePattern.any,
      input,
      HttpCodec.unused,
      HttpCodec.unused,
      HttpContentCodec.responseErrorCodec,
      doc,
      AuthType.None,
    )

  lazy val anyCliEndpoint: Gen[Any, CliReprOf[CliEndpoint]] =
    anyCodec.map(
      _.map(c => CliEndpoint.fromEndpoint(fromInputCodec(Doc.empty, c))),
    )

  lazy val tree = Tree[CliReprOf[Codec[_]]](atoms, mappers, operators, 3)

  lazy val anyCodec = tree.gen

  // Generators of HttpCodec.Atom
  lazy val atoms = List(anyEmpty, anyHalt, anyContent, anyContentStream, anyHeader, anyPath, anyQuery)

  lazy val anyEmpty: Gen[Any, CliReprOf[Codec[_]]] = Gen.const(CliRepr(HttpCodec.Empty, CliEndpoint.empty))

  lazy val anyHalt: Gen[Any, CliReprOf[Codec[_]]] = Gen.const(CliRepr(HttpCodec.Halt, CliEndpoint.empty))

  lazy val anyContent: Gen[Any, CliReprOf[Codec[_]]] =
    anySchema
      .zip(Gen.option(Gen.alphaNumericStringBounded(1, 30)))
      .zip(anyMediaType)
      .collect {
        case (schema, name, mediaType) if HttpContentCodec.fromSchema(schema).lookup(mediaType).isDefined =>
          CliRepr(
            HttpCodec.Content(HttpContentCodec.fromSchema(schema).only(mediaType), name),
            CliEndpoint(HttpOptions.Body(name.getOrElse(""), mediaType, schema) :: Nil),
          )
      }

  lazy val anyContentStream: Gen[Any, CliReprOf[Codec[_]]] =
    anySchema
      .zip(Gen.option(Gen.alphaNumericStringBounded(1, 30)))
      .zip(anyMediaType)
      .collect {
        case (schema, name, mediaType) if HttpContentCodec.fromSchema(schema).lookup(mediaType).isDefined =>
          CliRepr(
            HttpCodec.ContentStream(HttpContentCodec.fromSchema(schema).only(mediaType), name),
            CliEndpoint(HttpOptions.Body(name.getOrElse(""), mediaType, schema) :: Nil),
          )
      }

  lazy val anyHeader: Gen[Any, CliReprOf[Codec[_]]] =
    Gen.alphaNumericStringBounded(1, 30).map(_.toLowerCase).zip(anyTextCodec).map { case (name, codec) =>
      CliRepr(
        HttpCodec.Header(Header.Custom(name, "").headerType), // todo use schema bases header
        codec match {
          case _ => CliEndpoint(headers = HttpOptions.Header(name, codec) :: Nil)
        },
      )
    }

  lazy val anyPathCodec: Gen[Any, PathCodec[_]] =
    Gen.oneOf(
      anyTextCodec.zip(Gen.alphaNumericStringBounded(1, 30)).map { case (codec, name) =>
        OptionsGen.toPathCodec(name, codec)
      },
      Gen.const(PathCodec.trailing),
    )

  lazy val anyPath: Gen[Any, CliReprOf[Codec[_]]] =
    anyPathCodec.map { codec =>
      CliRepr(HttpCodec.Path(codec), CliEndpoint(url = HttpOptions.Path(codec) :: Nil))
    }

  lazy val anyQuery: Gen[Any, CliReprOf[Codec[_]]] =
    Gen.alphaNumericStringBounded(1, 30).zip(anyStandardType).map { case (name, schema0) =>
      val schema = schema0.asInstanceOf[Schema[Any]]
      val codec  = QueryCodec.query(name)(schema).asInstanceOf[HttpCodec.Query[Any]]
      CliRepr(
        codec,
        CliEndpoint(url = HttpOptions.Query(codec.codec.recordFields.head._2, name) :: Nil),
      )
    }

  // Mappers
  def mappers: List[Mapper[CliReprOf[Codec[_]], Any]] =
    List(transformOrFail, withDoc, withExamples).map(_.asInstanceOf[Mapper[CliReprOf[Codec[_]], Any]])

  def transformOrFail[A] = Mapper[CliReprOf[Codec[A]], Any](
    (repr, _: Any) => CliRepr(repr.value.transform((x: A) => x)((x: A) => x), repr.repr),
    Gen.empty,
  )

  def withDoc[A] = Mapper[CliReprOf[Codec[A]], Doc](
    (repr, doc) => CliRepr(repr.value ?? doc, repr.repr.describeOptions(doc)),
    anyDoc,
  )

  def withExamples[A]: Mapper[CliReprOf[Codec[A]], A] = Mapper(
    (repr: CliReprOf[Codec[A]], _: A) => CliRepr(repr.value.examples(Nil), repr.repr),
    Gen.empty,
  )

  // Operators
  lazy val operators = List(++(_, _), |(_, _))

  def ++(repr1: CliReprOf[Codec[_]], repr2: CliReprOf[Codec[_]]): CliReprOf[Codec[_]] =
    CliRepr(repr1.value ++ repr2.value, repr1.repr ++ repr2.repr)

  def |(repr1: CliReprOf[Codec[_]], repr2: CliReprOf[Codec[_]]): CliReprOf[Codec[_]] =
    CliRepr(repr1.value | repr2.value, repr1.repr ++ repr2.repr)

}
