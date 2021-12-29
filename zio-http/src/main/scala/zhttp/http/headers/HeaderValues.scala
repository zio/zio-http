package zhttp.http.headers

import io.netty.handler.codec.http.HttpHeaderValues

/**
 * List of commonly use HeaderValues. They are provided to reduce bugs caused by typos and also to improve performance.
 * `HeaderValues` arent encoded everytime one needs to send them over the wire.
 */
trait HeaderValues {
  final val ApplicationJson: CharSequence               = HttpHeaderValues.APPLICATION_JSON
  final val ApplicationXWWWFormUrlencoded: CharSequence = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED
  final val ApplicationOctetStream: CharSequence        = HttpHeaderValues.APPLICATION_OCTET_STREAM
  final val ApplicationXhtml: CharSequence              = HttpHeaderValues.APPLICATION_XHTML
  final val ApplicationXml: CharSequence                = HttpHeaderValues.APPLICATION_XML
  final val ApplicationZstd: CharSequence               = HttpHeaderValues.APPLICATION_ZSTD
  final val Attachment: CharSequence                    = HttpHeaderValues.ATTACHMENT
  final val Base64: CharSequence                        = HttpHeaderValues.BASE64
  final val Binary: CharSequence                        = HttpHeaderValues.BINARY
  final val Boundary: CharSequence                      = HttpHeaderValues.BOUNDARY
  final val Bytes: CharSequence                         = HttpHeaderValues.BYTES
  final val Charset: CharSequence                       = HttpHeaderValues.CHARSET
  final val Chunked: CharSequence                       = HttpHeaderValues.CHUNKED
  final val Close: CharSequence                         = HttpHeaderValues.CLOSE
  final val Compress: CharSequence                      = HttpHeaderValues.COMPRESS
  final val Continue: CharSequence                      = HttpHeaderValues.CONTINUE
  final val Deflate: CharSequence                       = HttpHeaderValues.DEFLATE
  final val XDeflate: CharSequence                      = HttpHeaderValues.X_DEFLATE
  final val File: CharSequence                          = HttpHeaderValues.FILE
  final val Filename: CharSequence                      = HttpHeaderValues.FILENAME
  final val FormData: CharSequence                      = HttpHeaderValues.FORM_DATA
  final val Gzip: CharSequence                          = HttpHeaderValues.GZIP
  final val Br: CharSequence                            = HttpHeaderValues.BR
  final val Zstd: CharSequence                          = HttpHeaderValues.ZSTD
  final val GzipDeflate: CharSequence                   = HttpHeaderValues.GZIP_DEFLATE
  final val XGzip: CharSequence                         = HttpHeaderValues.X_GZIP
  final val Identity: CharSequence                      = HttpHeaderValues.IDENTITY
  final val KeepAlive: CharSequence                     = HttpHeaderValues.KEEP_ALIVE
  final val MaxAge: CharSequence                        = HttpHeaderValues.MAX_AGE
  final val MaxStale: CharSequence                      = HttpHeaderValues.MAX_STALE
  final val MinFresh: CharSequence                      = HttpHeaderValues.MIN_FRESH
  final val MultipartFormData: CharSequence             = HttpHeaderValues.MULTIPART_FORM_DATA
  final val MultipartMixed: CharSequence                = HttpHeaderValues.MULTIPART_MIXED
  final val MustRevalidate: CharSequence                = HttpHeaderValues.MUST_REVALIDATE
  final val Name: CharSequence                          = HttpHeaderValues.NAME
  final val NoCache: CharSequence                       = HttpHeaderValues.NO_CACHE
  final val NoStore: CharSequence                       = HttpHeaderValues.NO_STORE
  final val NoTransform: CharSequence                   = HttpHeaderValues.NO_TRANSFORM
  final val None: CharSequence                          = HttpHeaderValues.NONE
  final val Zero: CharSequence                          = HttpHeaderValues.ZERO
  final val OnlyIfCached: CharSequence                  = HttpHeaderValues.ONLY_IF_CACHED
  final val Private: CharSequence                       = HttpHeaderValues.PRIVATE
  final val ProxyRevalidate: CharSequence               = HttpHeaderValues.PROXY_REVALIDATE
  final val Public: CharSequence                        = HttpHeaderValues.PUBLIC
  final val QuotedPrintable: CharSequence               = HttpHeaderValues.QUOTED_PRINTABLE
  final val SMaxAge: CharSequence                       = HttpHeaderValues.S_MAXAGE
  final val TextCss: CharSequence                       = HttpHeaderValues.TEXT_CSS
  final val TextHtml: CharSequence                      = HttpHeaderValues.TEXT_HTML
  final val TextEventStream: CharSequence               = HttpHeaderValues.TEXT_EVENT_STREAM
  final val TextPlain: CharSequence                     = HttpHeaderValues.TEXT_PLAIN
  final val Trailers: CharSequence                      = HttpHeaderValues.TRAILERS
  final val Upgrade: CharSequence                       = HttpHeaderValues.UPGRADE
  final val WebSocket: CharSequence                     = HttpHeaderValues.WEBSOCKET
  final val XmlHttpRequest: CharSequence                = HttpHeaderValues.XML_HTTP_REQUEST
}
