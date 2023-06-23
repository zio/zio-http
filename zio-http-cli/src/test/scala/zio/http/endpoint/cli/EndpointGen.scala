package zio.http.endpoint.cli

import zio.ZNothing
import zio.cli._
import zio.test._

import zio.schema._

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
  ): Endpoint[Input, ZNothing, ZNothing, EndpointMiddleware.None] =
    Endpoint(input, HttpCodec.unused, HttpCodec.unused, doc, EndpointMiddleware.None)

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
      .zip(Gen.option(anyMediaType))
      .map { case (schema, name, mediaType) =>
        CliRepr(
          HttpCodec.Content(schema, mediaType, name),
          CliEndpoint(HttpOptions.Body(name.getOrElse(""), mediaType, schema) :: Nil),
        )
      }

  lazy val anyContentStream: Gen[Any, CliReprOf[Codec[_]]] =
    anySchema
      .zip(Gen.option(Gen.alphaNumericStringBounded(1, 30)))
      .zip(Gen.option(anyMediaType))
      .map { case (schema, name, mediaType) =>
        CliRepr(
          HttpCodec.ContentStream(schema, mediaType, name),
          CliEndpoint(HttpOptions.Body(name.getOrElse(""), mediaType, schema) :: Nil),
        )
      }

  lazy val anyHeader: Gen[Any, CliReprOf[Codec[_]]] =
    Gen.alphaNumericStringBounded(1, 30).zip(anyTextCodec).map { case (name, codec) =>
      CliRepr(
        HttpCodec.Header(name, codec),
        codec match {
          case TextCodec.Constant(value) => CliEndpoint(headers = HttpOptions.HeaderConstant(name, value) :: Nil)
          case _                         => CliEndpoint(headers = HttpOptions.Header(name, codec) :: Nil)
        },
      )
    }

  lazy val anyPath: Gen[Any, CliReprOf[Codec[_]]] =
    anyTextCodec.zip(Gen.option(Gen.alphaNumericStringBounded(1, 30))).map {
      case (codec, Some(name))                       =>
        CliRepr(HttpCodec.Path(codec, Some(name)), CliEndpoint(url = HttpOptions.Path(name, codec) :: Nil))
      case (codec @ TextCodec.Constant(value), name) =>
        CliRepr(HttpCodec.Path(codec, name), CliEndpoint(url = HttpOptions.PathConstant(value) :: Nil))
      case (codec, name)                             =>
        CliRepr(HttpCodec.Path(codec, name), CliEndpoint.empty)
    }

  lazy val anyQuery: Gen[Any, CliReprOf[Codec[_]]] =
    Gen.alphaNumericStringBounded(1, 30).zip(anyTextCodec).map { case (name, codec) =>
      CliRepr(
        HttpCodec.Query(name, codec),
        codec match {
          case TextCodec.Constant(value) => CliEndpoint(url = HttpOptions.QueryConstant(name, value) :: Nil)
          case _                         => CliEndpoint(url = HttpOptions.Query(name, codec) :: Nil)
        },
      )
    }

  // Mappers
  def mappers: List[Mapper[CliReprOf[Codec[_]], Any]] =
    List(transformOrFail, withDoc, withExamples).map(_.asInstanceOf[Mapper[CliReprOf[Codec[_]], Any]])

  def transformOrFail[A] = Mapper[CliReprOf[Codec[A]], Any](
    (repr, _: Any) => CliRepr(repr.value.transform((x: A) => x, (x: A) => x), repr.repr),
    Gen.empty,
  )

  def withDoc[A] = Mapper[CliReprOf[Codec[A]], Doc](
    (repr, doc) => CliRepr(repr.value ?? doc, repr.repr ?? doc),
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
