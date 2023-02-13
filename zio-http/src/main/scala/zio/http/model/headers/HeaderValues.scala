package zio.http.model.headers

import io.netty.handler.codec.http.HttpHeaderValues
import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * List of commonly use HeaderValues. They are provided to reduce bugs caused by
 * typos and also to improve performance. `HeaderValues` arent encoded everytime
 * one needs to send them over the wire.
 */
trait HeaderValues {
  final val applicationJson: CharSequence               = HttpHeaderValues.APPLICATION_JSON
  final val applicationXWWWFormUrlencoded: CharSequence = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED
  final val applicationOctetStream: CharSequence        = HttpHeaderValues.APPLICATION_OCTET_STREAM
  final val applicationXhtml: CharSequence              = HttpHeaderValues.APPLICATION_XHTML
  final val applicationXml: CharSequence                = HttpHeaderValues.APPLICATION_XML
  final val applicationZstd: CharSequence               = HttpHeaderValues.APPLICATION_ZSTD
  final val attachment: CharSequence                    = HttpHeaderValues.ATTACHMENT
  final val base64: CharSequence                        = HttpHeaderValues.BASE64
  final val binary: CharSequence                        = HttpHeaderValues.BINARY
  final val boundary: CharSequence                      = HttpHeaderValues.BOUNDARY
  final val bytes: CharSequence                         = HttpHeaderValues.BYTES
  final val charset: CharSequence                       = HttpHeaderValues.CHARSET
  final val chunked: CharSequence                       = HttpHeaderValues.CHUNKED
  final val close: CharSequence                         = HttpHeaderValues.CLOSE
  final val compress: CharSequence                      = HttpHeaderValues.COMPRESS
  final val continue: CharSequence                      = HttpHeaderValues.CONTINUE
  final val deflate: CharSequence                       = HttpHeaderValues.DEFLATE
  final val xDeflate: CharSequence                      = HttpHeaderValues.X_DEFLATE
  final val file: CharSequence                          = HttpHeaderValues.FILE
  final val filename: CharSequence                      = HttpHeaderValues.FILENAME
  final val formData: CharSequence                      = HttpHeaderValues.FORM_DATA
  final val gzip: CharSequence                          = HttpHeaderValues.GZIP
  final val br: CharSequence                            = HttpHeaderValues.BR
  final val zstd: CharSequence                          = HttpHeaderValues.ZSTD
  final val gzipDeflate: CharSequence                   = HttpHeaderValues.GZIP_DEFLATE
  final val xGzip: CharSequence                         = HttpHeaderValues.X_GZIP
  final val identity: CharSequence                      = HttpHeaderValues.IDENTITY
  final val keepAlive: CharSequence                     = HttpHeaderValues.KEEP_ALIVE
  final val maxAge: CharSequence                        = HttpHeaderValues.MAX_AGE
  final val maxStale: CharSequence                      = HttpHeaderValues.MAX_STALE
  final val minFresh: CharSequence                      = HttpHeaderValues.MIN_FRESH
  final val multipartFormData: CharSequence             = HttpHeaderValues.MULTIPART_FORM_DATA
  final val multipartMixed: CharSequence                = HttpHeaderValues.MULTIPART_MIXED
  final val mustRevalidate: CharSequence                = HttpHeaderValues.MUST_REVALIDATE
  final val name: CharSequence                          = HttpHeaderValues.NAME
  final val noCache: CharSequence                       = HttpHeaderValues.NO_CACHE
  final val noStore: CharSequence                       = HttpHeaderValues.NO_STORE
  final val noTransform: CharSequence                   = HttpHeaderValues.NO_TRANSFORM
  final val none: CharSequence                          = HttpHeaderValues.NONE
  final val zero: CharSequence                          = HttpHeaderValues.ZERO
  final val onlyIfCached: CharSequence                  = HttpHeaderValues.ONLY_IF_CACHED
  final val `private`: CharSequence                     = HttpHeaderValues.PRIVATE
  final val proxyRevalidate: CharSequence               = HttpHeaderValues.PROXY_REVALIDATE
  final val public: CharSequence                        = HttpHeaderValues.PUBLIC
  final val quotedPrintable: CharSequence               = HttpHeaderValues.QUOTED_PRINTABLE
  final val sMaxAge: CharSequence                       = HttpHeaderValues.S_MAXAGE
  final val textCss: CharSequence                       = HttpHeaderValues.TEXT_CSS
  final val textHtml: CharSequence                      = HttpHeaderValues.TEXT_HTML
  final val textEventStream: CharSequence               = HttpHeaderValues.TEXT_EVENT_STREAM
  final val textPlain: CharSequence                     = HttpHeaderValues.TEXT_PLAIN
  final val trailers: CharSequence                      = HttpHeaderValues.TRAILERS
  final val upgrade: CharSequence                       = HttpHeaderValues.UPGRADE
  final val webSocket: CharSequence                     = HttpHeaderValues.WEBSOCKET
  final val xmlHttpRequest: CharSequence                = HttpHeaderValues.XML_HTTP_REQUEST
}
