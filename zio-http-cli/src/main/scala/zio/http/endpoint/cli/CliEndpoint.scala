package zio.http.endpoint.cli

import zio.http._
import zio.http.codec.HttpCodec.Query.QueryType
import zio.http.codec._
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
            case _: HttpOptions.Path          => true
            case _: HttpOptions.QueryConstant => true
            case _                            => false
          },
        )
        .map(_.name)
        .filter(_ != "")
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

  def describeOptions(description: Doc): CliEndpoint =
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
  def fromEndpoint[P, In, Err, Out](
    endpoint: Endpoint[P, In, Err, Out, _],
    getInput: Boolean = true,
  ): CliEndpoint =
    if (getInput) fromCodec(endpoint.input) ?? endpoint.documentation
    else fromCodec(endpoint.output) ?? endpoint.documentation

  def fromCodec[Input](input: HttpCodec[_, Input]): CliEndpoint = {
    input match {
      case atom: HttpCodec.Atom[_, _]            => fromAtom(atom)
      case HttpCodec.TransformOrFail(api, _, _)  => fromCodec(api)
      case HttpCodec.Annotated(in, metadata)     =>
        metadata match {
          case HttpCodec.Metadata.Documented(doc) => fromCodec(in) describeOptions doc
          case _                                  => fromCodec(in)
        }
      case HttpCodec.Fallback(left, right, _, _) => fromCodec(left) ++ fromCodec(right)
      case HttpCodec.Combine(left, right, _)     => fromCodec(left) ++ fromCodec(right)
      case _                                     => CliEndpoint.empty
    }
  }

  private def fromAtom[Input](input: HttpCodec.Atom[_, Input]): CliEndpoint = {
    input match {
      case HttpCodec.Content(codec, nameOption, _) =>
        val name = nameOption match {
          case Some(x) => x
          case None    => ""
        }
        CliEndpoint(body = HttpOptions.Body(name, codec.defaultMediaType, codec.defaultSchema) :: List())

      case HttpCodec.ContentStream(codec, nameOption, _) =>
        val name = nameOption match {
          case Some(x) => x
          case None    => ""
        }
        CliEndpoint(body = HttpOptions.Body(name, codec.defaultMediaType, codec.defaultSchema) :: List())

      case HttpCodec.Header(name, textCodec, _) if textCodec.isInstanceOf[TextCodec.Constant] =>
        CliEndpoint(headers =
          HttpOptions.HeaderConstant(name, textCodec.asInstanceOf[TextCodec.Constant].string) :: List(),
        )
      case HttpCodec.Header(name, textCodec, _)                                               =>
        CliEndpoint(headers = HttpOptions.Header(name, textCodec) :: List())
      case HttpCodec.Method(codec, _)                                                         =>
        codec.asInstanceOf[SimpleCodec[_, _]] match {
          case SimpleCodec.Specified(method: Method) =>
            CliEndpoint(methods = method)
          case _                                     => CliEndpoint.empty
        }

      case HttpCodec.Path(pathCodec, _) =>
        CliEndpoint(url = HttpOptions.Path(pathCodec) :: List())

      case HttpCodec.Query(queryType, _) =>
        queryType match {
          case QueryType.Primitive(name, codec)     =>
            CliEndpoint(url = HttpOptions.Query(name, codec) :: List())
          case record @ QueryType.Record(_)         =>
            val queryOptions = record.fieldAndCodecs.map { case (field, codec) =>
              HttpOptions.Query(field.name, codec)
            }
            CliEndpoint(url = queryOptions.toList)
          case QueryType.Collection(_, elements, _) =>
            val queryOptions =
              HttpOptions.Query(elements.name, elements.codec)
            CliEndpoint(url = queryOptions :: List())
        }

      case HttpCodec.Status(_, _) => CliEndpoint.empty

    }
  }
}
