package zio.http.datastar.model

import scala.language.implicitConversions

import zio._
import zio.json._

import zio.schema._
import zio.schema.annotation.fieldName
import zio.schema.codec.JsonCodec.jsonCodec

import zio.http.URL.Location
import zio.http.URL.Location.Relative
import zio.http._
import zio.http.internal.{HeaderOps, QueryOps, QueryParamEncoding, ThreadLocals}
import zio.http.template2.Js

final case class DatastarRequest(
  method: Method,
  url: URL,
  options: DatastarRequestOptions,
) extends QueryOps[DatastarRequest]
    with HeaderOps[DatastarRequest] {

  def baseUrl(url: URL): DatastarRequest = this.copy(url = this.url.copy(kind = url.kind))

  def renderUrl: String = DatastarRequest.encodeUrl(url)

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

object DatastarRequest {

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
      else QueryParamEncoding.encodeComponentInto(path.segments(idx), Charsets.Http, sb, "%20")
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

  def apply(method: Method, url: URL): DatastarRequest =
    DatastarRequest(method, url, DatastarRequestOptions.default)
}

final case class DatastarRequestOptions(
  contentType: MediaType = MediaType.application.json,
  filterSignals: Option[DatastarSignalFilter] = None,
  selector: Option[String] = None,
  @fieldName("headers")
  hdrs: Headers = Headers.empty,
  openWhenHidden: Boolean = false,
  retryInterval: Int = 1000,
  retryScaler: Int = 2,
  retryMaxWaitMs: Int = 30000,
  retryMaxCount: Int = 10,
  requestCancellation: DatastarRequestCancellation = DatastarRequestCancellation.Auto,
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

object DatastarRequestOptions {
  val default: DatastarRequestOptions                 = DatastarRequestOptions()
  implicit val headersSchema: Schema[Headers]         =
    Schema[Map[String, String]].transform[Headers](
      map => Headers.fromIterable(map.map { case (k, v) => Header.Custom(k, v) }),
      headers => headers.map(h => h.headerName -> h.renderedValue).toMap,
    )
  implicit val schema: Schema[DatastarRequestOptions] = DeriveSchema.gen

  implicit val json: JsonCodec[DatastarRequestOptions] = jsonCodec(schema)
}

final case class DatastarSignalFilter(
  include: String,
  exclude: Option[String] = None,
)

object DatastarSignalFilter {
  implicit val schema: Schema[DatastarSignalFilter] = DeriveSchema.gen
}

sealed trait DatastarRequestCancellation
object DatastarRequestCancellation {
  case object Auto             extends DatastarRequestCancellation
  case object Disabled         extends DatastarRequestCancellation
  case class Custom(value: Js) extends DatastarRequestCancellation

  implicit val schema: Schema[DatastarRequestCancellation] = Schema[String].transform[DatastarRequestCancellation](
    {
      case "Auto"     => Auto
      case "Disabled" => Disabled
      case other      => Custom(Js(other))
    },
    {
      case Auto          => "Auto"
      case Disabled      => "Disabled"
      case Custom(value) => value.value
    },
  )
}
