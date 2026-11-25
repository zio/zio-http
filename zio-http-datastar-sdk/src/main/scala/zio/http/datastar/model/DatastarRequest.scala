package zio.http.datastar.model

import scala.language.implicitConversions

import zio._
import zio.json._
import zio.json.ast.Json

import zio.schema._
import zio.schema.annotation.fieldName
import zio.schema.codec.JsonCodec.jsonCodec

import zio.http.URL.Location
import zio.http.URL.Location.Relative
import zio.http._
import zio.http.internal.{HeaderOps, QueryOps, QueryParamEncoding, ThreadLocals}
import zio.http.template2.{CssSelector, Js}

/**
 * A call of a Datastar action, such as `@get('/users')`: an HTTP method, a URL and the options of the call. `render` returns the action text, and an implicit conversion turns a request into `Js`, so it can be used as the value of an attribute such as `dataOn.click`.
 */
final case class DatastarRequest(
  method: Method,
  url: URL,
  options: DatastarRequestOptions,
) extends QueryOps[DatastarRequest]
    with HeaderOps[DatastarRequest] {

  /**
   * Returns a copy that takes its location kind from `url` (relative, or absolute with a scheme, host and port) and keeps the path, query parameters and fragment of this request.
   */
  def baseUrl(url: URL): DatastarRequest = this.copy(url = this.url.copy(kind = url.kind))

  /**
   * Returns the URL as it is written in the rendered action: the path, the query string, the fragment and, for an absolute URL, the scheme, host and port.
   */
  def renderUrl: String = DatastarRequest.encodeUrl(url)

  /**
   * Returns the action text, for example `@get('/a/b')`. The method name is written in lower case. When the options differ from the defaults, they follow the URL as a JSON object that holds only the fields that differ, for example `@get('/a', {"openWhenHidden":true})`.
   */
  def render: String =
    if (options == DatastarRequestOptions.default) s"""@${method.name.toLowerCase}('$renderUrl')"""
    else s"""@${method.name.toLowerCase}('$renderUrl', ${options.toJson})"""

  /**
   * Updates the current query parameters with new one, using the provided
   * update function passed.
   */
  override def updateQueryParams(f: QueryParams => QueryParams): DatastarRequest =
    this.copy(url = url.updateQueryParams(f))

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): DatastarRequest =
    this.copy(options = options.updateHeaders(update))

  /**
   * Returns the headers
   */
  override def headers: Headers = options.headers

  override def queryParameters: QueryParams = url.queryParams
}

/** Constructors and conversions for `DatastarRequest`. */
object DatastarRequest {

  /** Converts a request to `Js` that holds the text of `render`. */
  implicit def toJsExpr(request: DatastarRequest): Js =
    Js(request.render)

  private def encodePath(path: Path): java.lang.StringBuilder = {
    val sb      = ThreadLocals.stringBuilder
    if (path.hasLeadingSlash) sb.append('/')
    var idx     = 0
    val lastIdx = path.segments.length - 1
    while (idx <= lastIdx) {
      val segment = path.segments(idx)
      if (segment.nonEmpty && segment.head == '$') sb.append(segment)
      else QueryParamEncoding.encodeComponentInto(path.segments(idx), Charsets.Http, sb, "%20", isPath = true)
      if (path.hasTrailingSlash || idx != lastIdx) sb.append('/')
      idx += 1
    }
    sb
  }

  private def encodeUrl(url: URL): String = {
    val path =
      QueryParamEncoding.encode(
        encodePath(url.path),
        url.queryParams,
        Charsets.Http,
      )

    def fromAbsURL(abs: Location.Absolute, path: String) = {
      // Do not use `abs.portIfNotDefault` here. Setting a port in the URL that is the default
      // is an edge case. But checking it allocates an `Option` that is not needed in most cases.
      abs.originalPort match {
        case None =>
          val sb = ThreadLocals.stringBuilder.append(abs.scheme.encode).append("://").append(abs.host)
          if (path.nonEmpty && path.head != '/') sb.append('/')
          sb
        case port =>
          val sb = ThreadLocals.stringBuilder
            .append(abs.scheme.encode)
            .append("://")
            .append(abs.host)
            .append(':')
            .append(port.get)
          if (path.nonEmpty && path.head != '/') sb.append('/')
          sb
      }
    }

    url.kind match {
      case Location.Relative if url.fragment.isEmpty      =>
        path
      case Relative                                       =>
        ThreadLocals.stringBuilder.append(path).append('#').append(url.fragment.get.raw).toString
      case abs: Location.Absolute if url.fragment.isEmpty =>
        fromAbsURL(abs, path).append(path).toString
      case abs: Location.Absolute                         =>
        fromAbsURL(abs, path).append(path).append('#').append(url.fragment.get.raw).toString

    }
  }

  /**
   * Returns a request for `method` and `url` with `DatastarRequestOptions.default`.
   */
  def apply(method: Method, url: URL): DatastarRequest =
    DatastarRequest(method, url, DatastarRequestOptions.default)
}

/**
 * The options of a Datastar action call. They are rendered as a JSON object that has one field per option and leaves out the fields that still have their default value.
 *
 * @param hdrs
 *   The request headers. They are written under the name `headers`.
 * @param payload
 *   A JavaScript expression, written to the JSON object as a string.
 * @param cleanup
 *   A JavaScript expression, written to the JSON object as a string.
 */
final case class DatastarRequestOptions(
  contentType: MediaType = MediaType.application.json,
  filterSignals: Option[DatastarSignalFilter] = None,
  selector: Option[CssSelector] = None,
  @fieldName("headers")
  hdrs: Headers = Headers.empty,
  openWhenHidden: Boolean = false,
  retryInterval: Int = 1000,
  retryScaler: Int = 2,
  retryMaxWaitMs: Int = 30000,
  retryMaxCount: Int = 10,
  requestCancellation: DatastarRequestCancellation = DatastarRequestCancellation.Auto,
  retry: Option[DatastarRetry] = None,
  payload: Option[Js] = None,
  cleanup: Option[Js] = None,
) extends HeaderOps[DatastarRequestOptions] {

  /**
   * Returns the headers
   */
  override def headers: Headers = hdrs

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): DatastarRequestOptions =
    this.copy(hdrs = update(this.headers))
}

/** Codecs and schemas for `DatastarRequestOptions`. */
object DatastarRequestOptions {
  /** Options with every field at its default value. */
  val default: DatastarRequestOptions = DatastarRequestOptions()

  /**
   * A schema that reads and writes `Headers` as a map from header name to header value.
   */
  implicit val headersSchema: Schema[Headers] =
    Schema[Map[String, String]].transform[Headers](
      map => Headers.fromIterable(map.map { case (k, v) => Header.Custom(k, v) }),
      headers => headers.map(h => h.headerName -> h.renderedValue).toMap,
    )

  /** A schema that reads and writes `Js` as its source text. */
  implicit val jsSchema: Schema[Js] = Schema[String].transform[Js](
    Js(_),
    _.value,
  )

  /** The schema of `DatastarRequestOptions`, derived from its fields. */
  implicit val schema: Schema[DatastarRequestOptions] = DeriveSchema.gen

  private val fullCodec: JsonCodec[DatastarRequestOptions] = jsonCodec(schema)

  private lazy val defaultFields: Map[String, Json] =
    fullCodec.encoder.toJsonAST(default) match {
      case Right(Json.Obj(fields)) => fields.toMap
      case _                       => Map.empty
    }

  /**
   * Encodes options as a JSON object that holds only the fields whose value differs from `DatastarRequestOptions.default`, so the default options encode as `{}`.
   */
  implicit val jsonEncoder: JsonEncoder[DatastarRequestOptions] =
    JsonEncoder[Json].contramap[DatastarRequestOptions] { a =>
      fullCodec.encoder.toJsonAST(a) match {
        case Right(Json.Obj(fields)) =>
          Json.Obj(Chunk.fromIterable(fields.filter { case (k, v) => !defaultFields.get(k).contains(v) }))
        case Right(other)            => other
        case Left(_)                 => Json.Obj()
      }
    }

  /**
   * Decodes options from a JSON object. Fields that are missing take their default values.
   */
  implicit val jsonDecoder: JsonDecoder[DatastarRequestOptions] = fullCodec.decoder

  /**
   * A JSON codec for `DatastarRequestOptions` that combines `jsonEncoder` and `jsonDecoder`.
   */
  implicit val json: JsonCodec[DatastarRequestOptions] = JsonCodec(jsonEncoder, jsonDecoder)
}

/**
 * A filter for the signals that a request sends: `include` is a pattern and `exclude`, when defined, a second one. Both are passed to Datastar as strings.
 */
final case class DatastarSignalFilter(
  include: String,
  exclude: Option[String] = None,
)

/** Schemas for `DatastarSignalFilter`. */
object DatastarSignalFilter {
  /** The schema of `DatastarSignalFilter`, derived from its fields. */
  implicit val schema: Schema[DatastarSignalFilter] = DeriveSchema.gen
}

/**
 * The value of the `requestCancellation` option. It is written as the string `auto` for `Auto`, `disabled` for `Disabled`, and the text of the script for `Custom`.
 */
sealed trait DatastarRequestCancellation
/** Schemas and cases of `DatastarRequestCancellation`. */
object DatastarRequestCancellation {
  /** Written as `auto`. */
  case object Auto             extends DatastarRequestCancellation
  /** Written as `disabled`. */
  case object Disabled         extends DatastarRequestCancellation
  /** Written as the text of `value`. */
  case class Custom(value: Js) extends DatastarRequestCancellation

  /**
   * A schema that reads and writes a cancellation value as a string. Reading `auto` or `disabled` gives `Auto` or `Disabled`, and any other string gives a `Custom` with that text.
   */
  implicit val schema: Schema[DatastarRequestCancellation] = Schema[String].transform[DatastarRequestCancellation](
    {
      case "auto"     => Auto
      case "disabled" => Disabled
      case other      => Custom(Js(other))
    },
    {
      case Auto          => "auto"
      case Disabled      => "disabled"
      case Custom(value) => value.value
    },
  )
}

/**
 * The value of the `retry` option. It is written as the lower-case name of the case: `auto`, `error`, `always` or `never`.
 */
sealed trait DatastarRetry
/** Cases and schema of `DatastarRetry`. */
object DatastarRetry {
  /** Written as `auto`. */
  case object Auto   extends DatastarRetry
  /** Written as `error`. */
  case object Error  extends DatastarRetry
  /** Written as `always`. */
  case object Always extends DatastarRetry
  /** Written as `never`. */
  case object Never  extends DatastarRetry

  /**
   * A schema that reads and writes a retry value as its lower-case name. Reading any other string fails with a message that starts with `Invalid DatastarRetry value:`, repeats the string and ends with `Expected 'auto', 'error', 'always', or 'never'.`
   */
  implicit val schema: Schema[DatastarRetry] = Schema[String].transformOrFail[DatastarRetry](
    {
      case "auto"   => Right(Auto)
      case "error"  => Right(Error)
      case "always" => Right(Always)
      case "never"  => Right(Never)
      case other    => Left(s"Invalid DatastarRetry value: '$other'. Expected 'auto', 'error', 'always', or 'never'.")
    },
    {
      case Auto   => Right("auto")
      case Error  => Right("error")
      case Always => Right("always")
      case Never  => Right("never")
    },
  )
}
