package zio.http.datastar

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import zio._
import zio.json._

import zio.stream.ZStream

import zio.schema.Schema
import zio.schema.codec.json._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.template2._

trait DatastarPackageBase extends Attributes {
  self =>
  private val headers = Headers(
    Header.CacheControl.NoCache,
    Header.Connection.KeepAlive,
  )

  private[datastar] val scriptName: String

  private val DefaultDatastarVersion = "1.0.0-RC.6"

  /**
   * Script element that loads Datastar from CDN using the version
   * zio-http-datastar-sdk was built against.
   *
   * @example
   *   {{{head( datastarScript() )}}}
   */
  def datastarScript: Dom.Element.Script = datastarScript(DefaultDatastarVersion)

  /**
   * Script element that loads Datastar from CDN using a specific version. Must
   * be at least version 1.0.0-RC.6
   *
   * @param version
   *   The Datastar version to load (e.g., "1.0.0-RC.6")
   * @example
   *   {{{head( datastarScript("1.0.0-RC.6") )}}}
   */
  def datastarScript(version: String): Dom.Element.Script =
    script.externalModule(s"https://cdn.jsdelivr.net/gh/starfederation/datastar@$version/bundles/$scriptName")

  /**
   * Creates a complete HTML page template with a specific version of Datastar.
   *
   * @param headContent
   *   Additional content for the head element (e.g., meta tags, title, styles)
   * @param bodyContent
   *   Content for the body element
   * @param datastarVersion
   *   The Datastar version to load
   * @param language
   *   Language attribute for the html element
   * @example
   *   {{{ mainPage( headContent = Seq( title("My App"), meta.charset("UTF-8")
   *   ), bodyContent = Seq( div("Hello, Datastar!") ), datastarVersion =
   *   "1.0.0-RC.6", language = Some("en") ) }}}
   */
  def mainPage(
    headContent: Seq[Dom],
    bodyContent: Seq[Dom],
    datastarVersion: String = DefaultDatastarVersion,
    language: Option[String] = None,
  ): Dom =
    html(
      language.map(lang := _),
      head(datastarScript(datastarVersion), headContent),
      body(bodyContent),
    )

  val Signal: zio.http.datastar.signal.Signal.type                  = zio.http.datastar.signal.Signal
  val SignalUpdate: zio.http.datastar.signal.SignalUpdate.type      = zio.http.datastar.signal.SignalUpdate
  val SignalName: zio.http.datastar.signal.SignalName.type          = zio.http.datastar.signal.SignalName
  val DatastarRequest: zio.http.datastar.model.DatastarRequest.type = zio.http.datastar.model.DatastarRequest
  val DatastarRequestOptions: zio.http.datastar.model.DatastarRequestOptions.type           =
    zio.http.datastar.model.DatastarRequestOptions
  val DatastarSignalFilter: zio.http.datastar.model.DatastarSignalFilter.type               =
    zio.http.datastar.model.DatastarSignalFilter
  val DatastarRequestCancellation: zio.http.datastar.model.DatastarRequestCancellation.type =
    zio.http.datastar.model.DatastarRequestCancellation
  val ValueOrSignal: zio.http.datastar.model.ValueOrSignal.type = zio.http.datastar.model.ValueOrSignal

  type Signal[A]                   = zio.http.datastar.signal.Signal[A]
  type SignalUpdate[A]             = zio.http.datastar.signal.SignalUpdate[A]
  type SignalName                  = zio.http.datastar.signal.SignalName
  type DatastarRequest             = zio.http.datastar.model.DatastarRequest
  type DatastarRequestOptions      = zio.http.datastar.model.DatastarRequestOptions
  type DatastarSignalFilter        = zio.http.datastar.model.DatastarSignalFilter
  type DatastarRequestCancellation = zio.http.datastar.model.DatastarRequestCancellation
  type ValueOrSignal[A]            = zio.http.datastar.model.ValueOrSignal[A]

  val datastarCodec =
    HttpCodec.contentStream[ServerSentEvent[String]] ++
      HttpCodec
        .header(Header.ContentType)
        .const(Header.ContentType(MediaType.text.`event-stream`)) ++
      HttpCodec.header(Header.CacheControl).const(Header.CacheControl.NoCache) ++
      HttpCodec.header(Header.Connection).const(Header.Connection.KeepAlive)

  private val patchElementsCodec = (HttpCodec.content[Dom] ++
    HttpCodec
      .header(Header.ContentType)
      .const(Header.ContentType(MediaType.text.`html`)) ++
    HttpCodec.header(Header.CacheControl).const(Header.CacheControl.NoCache) ++
    HttpCodec.headerAs[CssSelector]("datastar-selector").optional ++
    HttpCodec.headerAs[ElementPatchMode]("datastar-mode").optional ++
    HttpCodec.headerAs[Boolean]("datastar-use-view-transition").optional)
    .transformOrFailLeft[DatastarEvent.PatchElements](_ => Left("Not implemented"))(event =>
      (
        event.elements,
        event.selector,
        if (event.mode == ElementPatchMode.Outer) None else Some(event.mode),
        if (event.useViewTransition) Some(true) else None,
      ),
    )

  private val executeScriptCodec = (HttpCodec.Content(
    HttpContentCodec.Choices(
      ListMap(
        MediaType.text.`javascript` ->
          BinaryCodecWithSchema(zio.http.codec.TextBinaryCodec.fromSchema[String](Schema[String]), Schema[String]),
      ),
    ),
    None,
  )
    ++ HttpCodec
      .header(Header.ContentType)
      .const(Header.ContentType(MediaType.text.`javascript`))
    ++ HttpCodec.header(Header.CacheControl).const(Header.CacheControl.NoCache)
    ++ HttpCodec.headerAs[String]("datastar-script-attributes").optional)
    .transformOrFail[DatastarEvent.ExecuteScript](_ => Left("Not implemented"))(event =>
      if (event.script.children.size == 1 && event.script.children.head.isInstanceOf[Dom.Text]) {
        val scriptContent = event.script.children.head.asInstanceOf[Dom.Text].content
        val attributes    =
          if (event.script.attributes.isEmpty) None
          else
            Some(
              zio.json.ast.Json
                .apply(event.script.attributes.map { case (k, v) =>
                  k -> zio.json.ast.Json.Str(v.toString)
                }.toList: _*)
                .toJson,
            )
        Right((scriptContent, attributes))
      } else {
        Left("Script must be a single text node.")
      },
    )

  private val patchSignalsCodec = (HttpCodec.Content(
    HttpContentCodec.Choices(
      ListMap(
        MediaType.application.`json` ->
          BinaryCodecWithSchema(zio.http.codec.TextBinaryCodec.fromSchema[String](Schema[String]), Schema[String]),
      ),
    ),
    None,
  ) ++
    HttpCodec
      .header(Header.ContentType)
      .const(Header.ContentType(MediaType.application.json)) ++
    HttpCodec.header(Header.CacheControl).const(Header.CacheControl.NoCache) ++
    HttpCodec.headerAs[Boolean]("datastar-only-if-missing").optional)
    .transformOrFailLeft[DatastarEvent.PatchSignals](_ => Left("Not implemented"))(event =>
      (
        event.signals,
        if (event.onlyIfMissing) Some(true) else None,
      ),
    )

  private val datastarEventMediaTypes = Chunk(
    Header.Accept.MediaTypeWithQFactor(MediaType.text.`html`, None),
    Header.Accept.MediaTypeWithQFactor(MediaType.application.`json`, None),
    Header.Accept.MediaTypeWithQFactor(MediaType.text.`javascript`, None),
  )

  val datastarEventCodec =
    ((patchElementsCodec | executeScriptCodec).transform(_.merge) {
      case e: DatastarEvent.PatchElements => Left(e)
      case e: DatastarEvent.ExecuteScript => Right(e)
      case e: DatastarEvent.PatchSignals  => throw new Exception("Unreachable")
    } | patchSignalsCodec).transform(_.merge) {
      case e: DatastarEvent.PatchElements => Left(e)
      case e: DatastarEvent.ExecuteScript => Left(e)
      case e: DatastarEvent.PatchSignals  => Right(e)
    }

  implicit class EndpointExtensions(endpoint: Endpoint.type) {
    def datastarEvents[Input](
      route: RoutePattern[Input],
    ): Endpoint[Input, Input, ZNothing, ZStream[Any, Nothing, DatastarEvent], AuthType.None.type] =
      Endpoint(
        route,
        route.toHttpCodec,
        datastarCodec.transformOrFailLeft(_ => Left("Not implemented"))(_.map(_.toServerSentEvent)),
        HttpCodec.unused,
        HttpContentCodec.responseErrorCodec,
        Doc.empty,
        AuthType.None,
      )

    def datastarEvent[Input](
      route: RoutePattern[Input],
    ): Endpoint[Input, Input, ZNothing, DatastarEvent, AuthType.None.type] =
      Endpoint(
        route,
        route.toHttpCodec,
        datastarEventCodec,
        HttpCodec.unused,
        HttpContentCodec.responseErrorCodec,
        Doc.empty,
        AuthType.None,
      )

    def datastar[Input](
      route: RoutePattern[Input],
    ): Endpoint[Input, Input, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], AuthType.None.type] =
      Endpoint(
        route,
        route.toHttpCodec,
        datastarCodec,
        HttpCodec.unused,
        HttpContentCodec.responseErrorCodec,
        Doc.empty,
        AuthType.None,
      )
  }

  implicit class DatastarHeaderOps(header: Header.type) {
    def signalHeader(signalName: SignalName): Header =
      Header.Custom("datastar-signal", signalName.ref)

    def signalHeader(signal: Signal[_]): Header =
      Header.Custom("datastar-signal", signal.name.ref)
  }

  implicit class DatastarEndpointOps[PathInput](
    endpoint: Endpoint[PathInput, _, _, _, _],
  ) {

    private def makeRequest(valuesIt: Iterator[ValueOrSignal[Any]]) = {
      val rendered     = endpoint.route.pathCodec.render
      val replacements = Chunk.newBuilder[String]
      replacements.sizeHint(rendered.count(_ == '{'))

      for {
        (segment, transform) <- endpoint.route.pathCodec.segmentsWithTransformEncode
      } {
        segment match {
          case SegmentCodec.Empty                => ()
          case _: SegmentCodec.Literal           => ()
          case SegmentCodec.Trailing             =>
            valuesIt.next() match {
              case zio.http.datastar.model.ValueOrSignal.Value(value)        =>
                replacements += value.toString
              case zio.http.datastar.model.ValueOrSignal.SignalValue(signal) =>
                replacements += signal.ref.replace("$", "\\$")
            }
          case _: SegmentCodec.Combined[_, _, _] =>
            throw new IllegalArgumentException(
              "Internal transformation error. Combined should not be present in path codec segments.",
            )
          case _                                 =>
            val v = valuesIt.next()
            transform match {
              case Some(f) =>
                v match {
                  case zio.http.datastar.model.ValueOrSignal.Value(v)         =>
                    val transformed = f(v)
                    transformed match {
                      case Left(value)  => throw new IllegalArgumentException(s"Transform failed with error: $value")
                      case Right(value) => replacements += segment.asInstanceOf[SegmentCodec[Any]].format(value).encode
                    }
                  case zio.http.datastar.model.ValueOrSignal.SignalValue(sig) =>
                    throw new IllegalArgumentException(s"Cannot apply transform to a signal. Signal: ${sig.ref}")
                }
              case None    =>
                v match {
                  case zio.http.datastar.model.ValueOrSignal.Value(v)         =>
                    replacements += segment.asInstanceOf[SegmentCodec[Any]].format(v).encode
                  case zio.http.datastar.model.ValueOrSignal.SignalValue(sig) =>
                    replacements += sig.ref.replace("$", "\\$")
                }
            }

        }
      }
      val url = replacements.result().foldLeft(rendered) { (acc, replacement) =>
        acc.replaceFirst("\\{[^}]+\\}", replacement)
      }
      DatastarRequest(endpoint.route.method, URL(Path(url)))
    }

    def datastarRequest(input: ValueOrSignal[PathInput]): DatastarRequest = {
      val url = input match {
        case zio.http.datastar.model.ValueOrSignal.Value(value: PathInput @unchecked) =>
          endpoint.route
            .format(value)
            .getOrElse(
              throw new IllegalArgumentException("Failed to encode path input."),
            )
            .encode
        case zio.http.datastar.model.ValueOrSignal.SignalValue(signal)                =>
          endpoint.route.pathCodec.render.replaceAll("\\{[^}]+\\}", signal.ref.replace("$", "\\$"))
      }
      DatastarRequest(endpoint.route.method, URL(Path(url)))
    }

    def datastarRequest[A, B](a: ValueOrSignal[A], b: ValueOrSignal[B])(implicit
      ev: (A, B) <:< PathInput,
    ): DatastarRequest =
      if (a.isValue && b.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val url = endpoint.route
          .encode(ev((va, vb)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }

    def datastarRequest[A, B, C](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
    )(implicit
      ev: (A, B, C) <:< PathInput,
    ): DatastarRequest = {
      if (a.isValue && b.isValue && c.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
    )(implicit
      ev: (A, B, C, D) <:< PathInput,
    ): DatastarRequest = {
      if (a.isValue && b.isValue && c.isValue && d.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
    )(implicit
      ev: (A, B, C, D, E) <:< PathInput,
    ): DatastarRequest = {
      if (a.isValue && b.isValue && c.isValue && d.isValue && e.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
    )(implicit
      ev: (A, B, C, D, E, F) <:< PathInput,
    ): DatastarRequest = {
      if (a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
    )(implicit
      ev: (A, B, C, D, E, F, G) <:< PathInput,
    ): DatastarRequest = {
      if (a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
    )(implicit
      ev: (A, B, C, D, E, F, G, H) <:< PathInput,
    ): DatastarRequest = {
      if (a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i, j).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J, K](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
      k: ValueOrSignal[K],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J, K) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue && k.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val vk  = k.asInstanceOf[ValueOrSignal.Value[K]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj, vk)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i, j, k).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J, K, L](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
      k: ValueOrSignal[K],
      l: ValueOrSignal[L],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J, K, L) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue && k.isValue && l.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val vk  = k.asInstanceOf[ValueOrSignal.Value[K]].value
        val vl  = l.asInstanceOf[ValueOrSignal.Value[L]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj, vk, vl)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i, j, k, l).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J, K, L, M](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
      k: ValueOrSignal[K],
      l: ValueOrSignal[L],
      m: ValueOrSignal[M],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J, K, L, M) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue && k.isValue && l.isValue && m.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val vk  = k.asInstanceOf[ValueOrSignal.Value[K]].value
        val vl  = l.asInstanceOf[ValueOrSignal.Value[L]].value
        val vm  = m.asInstanceOf[ValueOrSignal.Value[M]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj, vk, vl, vm)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i, j, k, l, m).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J, K, L, M, N](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
      k: ValueOrSignal[K],
      l: ValueOrSignal[L],
      m: ValueOrSignal[M],
      n: ValueOrSignal[N],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue && k.isValue && l.isValue && m.isValue && n.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val vk  = k.asInstanceOf[ValueOrSignal.Value[K]].value
        val vl  = l.asInstanceOf[ValueOrSignal.Value[L]].value
        val vm  = m.asInstanceOf[ValueOrSignal.Value[M]].value
        val vn  = n.asInstanceOf[ValueOrSignal.Value[N]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj, vk, vl, vm, vn)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i, j, k, l, m, n).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
      k: ValueOrSignal[K],
      l: ValueOrSignal[L],
      m: ValueOrSignal[M],
      n: ValueOrSignal[N],
      o: ValueOrSignal[O],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue && k.isValue && l.isValue && m.isValue && n.isValue && o.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val vk  = k.asInstanceOf[ValueOrSignal.Value[K]].value
        val vl  = l.asInstanceOf[ValueOrSignal.Value[L]].value
        val vm  = m.asInstanceOf[ValueOrSignal.Value[M]].value
        val vn  = n.asInstanceOf[ValueOrSignal.Value[N]].value
        val vo  = o.asInstanceOf[ValueOrSignal.Value[O]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj, vk, vl, vm, vn, vo)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt = List(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }

    def datastarRequest[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](
      a: ValueOrSignal[A],
      b: ValueOrSignal[B],
      c: ValueOrSignal[C],
      d: ValueOrSignal[D],
      e: ValueOrSignal[E],
      f: ValueOrSignal[F],
      g: ValueOrSignal[G],
      h: ValueOrSignal[H],
      i: ValueOrSignal[I],
      j: ValueOrSignal[J],
      k: ValueOrSignal[K],
      l: ValueOrSignal[L],
      m: ValueOrSignal[M],
      n: ValueOrSignal[N],
      o: ValueOrSignal[O],
      p: ValueOrSignal[P],
    )(implicit
      ev: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) <:< PathInput,
    ): DatastarRequest = {
      if (
        a.isValue && b.isValue && c.isValue && d.isValue && e.isValue && f.isValue && g.isValue && h.isValue && i.isValue && j.isValue && k.isValue && l.isValue && m.isValue && n.isValue && o.isValue && p.isValue
      ) {
        val va  = a.asInstanceOf[ValueOrSignal.Value[A]].value
        val vb  = b.asInstanceOf[ValueOrSignal.Value[B]].value
        val vc  = c.asInstanceOf[ValueOrSignal.Value[C]].value
        val vd  = d.asInstanceOf[ValueOrSignal.Value[D]].value
        val ve  = e.asInstanceOf[ValueOrSignal.Value[E]].value
        val vf  = f.asInstanceOf[ValueOrSignal.Value[F]].value
        val vg  = g.asInstanceOf[ValueOrSignal.Value[G]].value
        val vh  = h.asInstanceOf[ValueOrSignal.Value[H]].value
        val vi  = i.asInstanceOf[ValueOrSignal.Value[I]].value
        val vj  = j.asInstanceOf[ValueOrSignal.Value[J]].value
        val vk  = k.asInstanceOf[ValueOrSignal.Value[K]].value
        val vl  = l.asInstanceOf[ValueOrSignal.Value[L]].value
        val vm  = m.asInstanceOf[ValueOrSignal.Value[M]].value
        val vn  = n.asInstanceOf[ValueOrSignal.Value[N]].value
        val vo  = o.asInstanceOf[ValueOrSignal.Value[O]].value
        val vp  = p.asInstanceOf[ValueOrSignal.Value[P]].value
        val url = endpoint.route
          .encode(ev((va, vb, vc, vd, ve, vf, vg, vh, vi, vj, vk, vl, vm, vn, vo, vp)))
          .getOrElse(
            throw new IllegalArgumentException("Failed to encode path input."),
          )
          ._2
          .encode
        DatastarRequest(endpoint.route.method, URL(Path(url)))
      } else {
        val valuesIt =
          List(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p).asInstanceOf[List[ValueOrSignal[Any]]].iterator
        makeRequest(valuesIt)
      }
    }
  }

  def event[R, In](h: Handler[R, Response, In, DatastarEvent]): Handler[R, Response, In, Response] =
    Handler.scoped[R] {
      handler { (in: In) =>
        for {
          r     <- ZIO.environment[Scope with R]
          event <- h(in).provideEnvironment(r)
          response = datastarEventCodec.encodeResponse(
            event,
            datastarEventMediaTypes,
            zio.http.codec.CodecConfig.defaultConfig,
          )
        } yield response
      }
    }

  def events[R, R1, In](
    h: Handler[R, Nothing, In, Unit],
  )(implicit ev: R <:< R1 with Datastar): Handler[R1, Nothing, In, Response] =
    Handler.scoped[R1] {
      handler { (in: In) =>
        for {
          datastar <- Datastar.make
          queue    = datastar.queue
          response =
            Response
              .fromServerSentEvents(ZStream.fromQueue(queue).takeWhile(_ ne Datastar.done))
              .addHeaders(headers)
          _ <- (h(in).provideSomeEnvironment[R1 with Scope](
            _.add[Datastar](datastar).asInstanceOf[ZEnvironment[R with Scope]],
          ) *> queue.offer(Datastar.done)).forkScoped
        } yield response
      }
    }

  @deprecated("Use events instead", "3.6.0")
  def eventStream[R, R1](
    e: ZIO[R, Nothing, Unit],
  )(implicit ev: R <:< R1 with Datastar): ZIO[R1 with Scope, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    events[R, R1](e)

  def events[R, R1](
    e: ZIO[R, Nothing, Unit],
  )(implicit ev: R <:< R1 with Datastar): ZIO[R1 with Scope, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    for {
      datastar <- Datastar.make
      queue = datastar.queue
      _ <- (e.provideSomeEnvironment[R1 with Scope](
        _.add[Datastar](datastar).asInstanceOf[ZEnvironment[R with Scope]],
      ) *> queue.offer(Datastar.done)).forkScoped
    } yield ZStream.fromQueue(queue).takeWhile(_ ne Datastar.done)

  def events[R](
    e: ZStream[R, Nothing, DatastarEvent],
  ): ZIO[R, Nothing, ZStream[Any, Nothing, ServerSentEvent[String]]] =
    ZIO.environmentWith[R](e.map(_.toServerSentEvent).provideEnvironment(_))

  def events[R, R1, In](
    h: Handler[R, Nothing, In, ZStream[R1, Nothing, DatastarEvent]],
  ): Handler[R with R1, Nothing, In, Response] =
    Handler.scoped[R with R1] {
      handler { (in: In) =>
        for {
          r      <- ZIO.environment[Scope & R]
          r1     <- ZIO.environment[R1]
          stream <- h(in).provideEnvironment(r)
          sseStream: ZStream[Any, Nothing, ServerSentEvent[String]] =
            stream.map(_.toServerSentEvent).provideEnvironment(r1)
        } yield Response
          .fromServerSentEvents(sseStream)
          .addHeaders(headers)
      }
    }

  def readSignals[T: Schema](request: Request): IO[String, T] =
    if (request.method == Method.GET) {
      ZIO.fromEither {
        request
          .header[String]("datastar")
          .left
          .map(_.getMessage())
          .flatMap(_.fromJson[T](zio.schema.codec.JsonCodec.jsonDecoder(Schema[T])))
      }
    } else {
      request.body.asJson[T].mapError(_.getMessage)
    }

  implicit class RequestOps(req: Request) {
    def readSignals[T: Schema]: IO[String, T] = self.readSignals[T](req)
  }
}
