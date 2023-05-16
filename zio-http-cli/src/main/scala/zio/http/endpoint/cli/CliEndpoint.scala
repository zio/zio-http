package zio.http.endpoint.cli

import scala.util.Try

import zio.cli._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.codec.internal._
import zio.http.endpoint._


private[cli] final case class CliEndpoint(
  body: List[HttpOptions.Body[_]] = List.empty,
  headers: List[HttpOptions.HeaderOptions] = List.empty,
  methods: Method = Method.GET,
  url: List[HttpOptions.URLOptions] = List.empty,
  commandNameSegments: List[String] = List.empty,
  doc: Doc = Doc.empty,
) {
  self =>

  def ++(that: CliEndpoint): CliEndpoint =
    CliEndpoint(
      self.body ++ that.body,
      self.headers ++ that.headers,
      if(that.methods == Method.GET) self.methods else that.methods,
      self.url ++ that.url,
      self.commandNameSegments ++ that.commandNameSegments,
      self.doc, // TODO add doc
    )

  def ??(doc: Doc): CliEndpoint = self.copy(doc = doc)

  lazy val commandName: String = {
    {methods match {
        case Method.POST  => "create"
        case Method.PUT   => "update"
        case method       => method.name.toLowerCase
      }} + " " + url.map(_.tag).fold("")(_ + _)
  }

  lazy val getOptions: List[HttpOptions] = url ++ headers ++ body

  def describeOptions(description: Doc) = self.copy(doc = doc + description)

  lazy val optional: CliEndpoint =
    CliEndpoint(
      //{
      //  case (Some(a), request) => self.embed(a, request)
      //  case (None, request)    => request
      //},
      //self.options.optional
    )
/*
  def transform[B](f: A => B, g: B => A): CliEndpoint =
    CliEndpoint(
      //(b, request) => self.embed(g(b), request),
      self.options.map(f),
      self.commandNameSegments,
      self.doc,
    )*/

}

private[cli] object CliEndpoint {

  def empty: CliEndpoint = CliEndpoint()

  def fromEndpoint[In, Err, Out, M <: EndpointMiddleware](endpoint: Endpoint[In, Err, Out, M]): CliEndpoint =
    fromInput(endpoint.input) ?? endpoint.doc

  def fromInput[Input](input: HttpCodec[_, Input]): CliEndpoint = {
    input match {
      case atom: HttpCodec.Atom[_, _]               => fromAtom(atom)
      case HttpCodec.TransformOrFail(api, _, _)     => fromInput(api)
      case HttpCodec.WithDoc(in, doc)               => fromInput(in) describeOptions doc
      case HttpCodec.WithExamples(in, _)            => fromInput(in)
      case HttpCodec.Fallback(left, right)          => fromInput(left) ++ fromInput(right)
      case HttpCodec.Combine(left, right, _)        => fromInput(left) ++ fromInput(right)
      case _                                        => CliEndpoint.empty          
    }
  }

  private def fromAtom[Input](input: HttpCodec.Atom[_, Input]): CliEndpoint = {
    input match {
      case HttpCodec.Content(schema, mediaType, nameOption, _)       => {
        val name = nameOption match {
          case Some(x) => x
          case None => ""
        }
        CliEndpoint(body = HttpOptions.Body(name, mediaType, schema) :: List())
      }
        
      case HttpCodec.ContentStream(schema, mediaType, nameOption, _) => {
        val name = nameOption match {
          case Some(x) => x
          case None => ""
        }
        CliEndpoint(body = HttpOptions.Body(name, mediaType, schema) :: List())
      }

      case HttpCodec.Header(name, textCodec, _)     =>
        CliEndpoint(headers = HttpOptions.Header(name, textCodec) :: List())
      case HttpCodec.Method(codec, _)               =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method: Method)  =>
            CliEndpoint(methods = method)
          case _     => CliEndpoint.empty
        }
      
      case HttpCodec.Path(textCodec, Some(name), _) =>
        CliEndpoint(url = HttpOptions.Path(name, textCodec) :: List())
      case HttpCodec.Path(textCodec, None, _)       =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.Constant(string) =>
            CliEndpoint(url = HttpOptions.PathConstant(string) :: List())
          case _                          => CliEndpoint.empty
        }
      
      case HttpCodec.Query(name, textCodec, _)      =>
        CliEndpoint(url = HttpOptions.Query(name, textCodec) :: List())
        
      case HttpCodec.Status(_, _)                   => CliEndpoint.empty

    }
  }
}
