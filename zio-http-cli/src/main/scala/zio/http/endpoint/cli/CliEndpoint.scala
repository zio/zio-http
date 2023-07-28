package zio.http.endpoint.cli

import scala.util.Try

import zio.cli._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.codec.internal._
import zio.http.endpoint._

/**
 * Represents the input or output of a Endpoint.
 */

private[cli] final case class CliEndpoint(
  body: List[HttpOptions.Body[_]] = List.empty,
  headers: List[HttpOptions.HeaderOptions] = List.empty,
  methods: Method = Method.GET,
  url: List[HttpOptions.URLOptions] = List.empty,
  doc: Doc = Doc.empty,
) {
  self =>

  def ++(that: CliEndpoint): CliEndpoint =
    CliEndpoint(
      self.body ++ that.body,
      self.headers ++ that.headers,
      if (that.methods == Method.GET) self.methods else that.methods,
      self.url ++ that.url,
      self.doc + that.doc,
    )

  def ??(doc: Doc): CliEndpoint = self.copy(doc = doc)

  def commandName(cliStyle: Boolean): String =
    if (cliStyle) {
      (methods match {
        case Method.POST => "create"
        case Method.PUT  => "update"
        case method      => method.name.toLowerCase
      }) :: url
        .filter(
          _ match {
            case _: HttpOptions.PathConstant  => true
            case _: HttpOptions.QueryConstant => true
            case _                            => false
          },
        )
        .map(_.name)
    }.mkString("-")
    else {
      {
        methods match {
          case Method.POST => "create"
          case Method.PUT  => "update"
          case method      => method.name.toLowerCase
        }
      } + " " + url.map(_.tag).fold("")(_ + _)
    }

  lazy val getOptions: List[HttpOptions] = url ++ headers ++ body

  def describeOptions(description: Doc) =
    self.copy(
      body = self.body.map(_ ?? description),
      headers = self.headers.map(_ ?? description),
      url = self.url.map(_ ?? description),
    )

}

private[cli] object CliEndpoint {

  def empty: CliEndpoint = CliEndpoint()

  /*
   * Extract the information of input or output of an Endpoint.
   */
  def fromEndpoint[P, In, Err, Out, M <: EndpointMiddleware](
    endpoint: Endpoint[P, In, Err, Out, M],
    getInput: Boolean = true,
  ): CliEndpoint =
    if (getInput) fromCodec(endpoint.input) ?? endpoint.doc
    else fromCodec(endpoint.output) ?? endpoint.doc

  def fromCodec[Input](input: HttpCodec[_, Input]): CliEndpoint = {
    input match {
      case atom: HttpCodec.Atom[_, _]           => fromAtom(atom)
      case HttpCodec.TransformOrFail(api, _, _) => fromCodec(api)
      case HttpCodec.WithDoc(in, doc)           => fromCodec(in) describeOptions doc
      case HttpCodec.WithExamples(in, _)        => fromCodec(in)
      case HttpCodec.Fallback(left, right)      => fromCodec(left) ++ fromCodec(right)
      case HttpCodec.Combine(left, right, _)    => fromCodec(left) ++ fromCodec(right)
      case _                                    => CliEndpoint.empty
    }
  }

  private def fromAtom[Input](input: HttpCodec.Atom[_, Input]): CliEndpoint = {
    input match {
      case HttpCodec.Content(schema, mediaType, nameOption, _) => {
        val name = nameOption match {
          case Some(x) => x
          case None    => ""
        }
        CliEndpoint(body = HttpOptions.Body(name, mediaType, schema) :: List())
      }

      case HttpCodec.ContentStream(schema, mediaType, nameOption, _) => {
        val name = nameOption match {
          case Some(x) => x
          case None    => ""
        }
        CliEndpoint(body = HttpOptions.Body(name, mediaType, schema) :: List())
      }

      case HttpCodec.Header(name, textCodec, _) =>
        CliEndpoint(headers = HttpOptions.Header(name, textCodec) :: List())
      case HttpCodec.Method(codec, _)           =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method: Method) =>
            CliEndpoint(methods = method)
          case _                                     => CliEndpoint.empty
        }

      case HttpCodec.Path(textCodec, Some(name), _) =>
        CliEndpoint(url = HttpOptions.Path(name, textCodec) :: List())
      case HttpCodec.Path(textCodec, None, _)       =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.Constant(value) => CliEndpoint(url = HttpOptions.PathConstant(value) :: List())
          case _                         => CliEndpoint.empty
        }

      case HttpCodec.Query(name, textCodec, _) =>
        textCodec.asInstanceOf[TextCodec[_]] match {
          case TextCodec.Constant(value) => CliEndpoint(url = HttpOptions.QueryConstant(name, value) :: List())
          case _                         => CliEndpoint(url = HttpOptions.Query(name, textCodec) :: List())
        }

      case HttpCodec.Status(_, _) => CliEndpoint.empty

    }
  }
}
