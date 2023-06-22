package zio.http.endpoint.cli

import zio.http.codec._
import zio.test.Gen
import zio.cli._
import zio.http._
import zio._
import zio.stream._
import AuxGen._
import CliRepr._

/**
 * Constructs a Gen[Options[CliRequest], CliEndpoint]
 */

object OptionsGen {

    lazy val anyBodyOption: Gen[Any, CliReprOf[Options[Retriever]]] = 
        Gen.alphaNumericStringBounded(1, 30)
            .zip(Gen.option(anyMediaType))
            .zip(anySchema)
            .map {
            case (name, mediaType, schema) => {
                val body =  HttpOptions.Body(name, mediaType, schema)
                CliRepr(
                    body.options, 
                    CliEndpoint(body = body::Nil))
            }
        }

    lazy val anyHeaderOption: Gen[Any, CliReprOf[Options[Headers]]] = 
        Gen.alphaNumericStringBounded(1, 30).zip(anyTextCodec).map {
            case (name, TextCodec.Constant(value)) => CliRepr(
                Options.Empty.map( _ => Headers(name, value)), 
                CliEndpoint(headers = HttpOptions.HeaderConstant(name, value)::Nil))
            case (name, codec) => CliRepr(
                HttpOptions
                    .optionsFromCodec(codec)(name)
                    .map( value => Headers(name, codec.asInstanceOf[TextCodec[value.type]].encode(value))), 
                CliEndpoint(headers = HttpOptions.Header(name, codec)::Nil))
        }


    lazy val anyURLOption: Gen[Any, CliReprOf[Options[Path.Segment]]] =
        Gen.oneOf(
            Gen.alphaNumericStringBounded(1, 30).zip(anyTextCodec).map {
            case (_, TextCodec.Constant(value)) => CliRepr(
                Options.Empty.map( _ => Path.Segment(value)), 
                CliEndpoint(url = HttpOptions.PathConstant(value)::Nil))
            case (name, codec) => CliRepr(
                HttpOptions
                    .optionsFromCodec(codec)(name)
                    .map( value => Path.Segment(codec.asInstanceOf[TextCodec[value.type]].encode(value))), 
                CliEndpoint(url = HttpOptions.Path(name, codec)::Nil))
            },
            Gen.alphaNumericStringBounded(1, 30).zip(anyTextCodec).map {
            case (name, TextCodec.Constant(value)) => CliRepr(
                Options.Empty.map( _ => Path.Segment(value)),
                CliEndpoint(url = HttpOptions.QueryConstant(name, value)::Nil))
            case (name, codec) => CliRepr(
                HttpOptions
                    .optionsFromCodec(codec)(name)
                    .map( value => Path.Segment(codec.asInstanceOf[TextCodec[value.type]].encode(value))), 
                CliEndpoint(url = HttpOptions.Query(name, codec)::Nil))
            }     
        )

    lazy val anyMethod: Gen[Any, CliReprOf[Method]] = 
        Gen.fromIterable(List(Method.GET, Method.DELETE, Method.POST, Method.PUT))
            .map {
                case method => CliRepr(method, CliEndpoint(methods = method))
            }

    lazy val anyCliEndpoint: Gen[Any, CliReprOf[Options[CliRequest]]] = 
        Gen.listOf(anyBodyOption)
            .zip(Gen.listOf(anyHeaderOption))
            .zip(Gen.listOf(anyURLOption))
            .zip(anyMethod)
            .map {
                case (body, header, url, method) => CliRepr(
                    (url.map(_.value)
                            .foldRight(Options.Empty.map(_ => List.empty[Path.Segment])) {
                                case (segment, list) => (segment ++ list).map {
                                    case (segment, list) => segment :: list
                                }
                            }
                            .map(_.toVector)
                            .map(Path(_))
                        ++ header
                            .map(_.value)
                            .foldLeft(Options.Empty.map(_ => Headers.empty)) {
                                case (headers, header) => (headers ++ header).map {
                                    case (headers, header) => headers ++ header
                                }
                            }
                        ++ body
                            .map(_.value)
                            .foldLeft(Options.Empty.map(_ => Chunk.empty[Retriever])) {
                            case (chunk, retriever) => (chunk ++ retriever).map {
                                case (chunk, retriever) => chunk ++ Chunk(retriever)
                            }})
                        .map { //(body, header, url)
                            case (url, header, body) => CliRequest(body, header, method.value, URL(url))
                        }, //body.map(_.repr)  ++++ header.map(_.repr) ++ url.map(_.repr)        CliRequest(body, header, method.value, URL(url))
                    ( body.map(_.repr)  ++ header.map(_.repr) ++ url.map(_.repr) ++ List(method.repr)).foldLeft(CliEndpoint.empty) {
                        case (cli1, cli2) => cli1 ++ cli2
                    }
                )
            }

}