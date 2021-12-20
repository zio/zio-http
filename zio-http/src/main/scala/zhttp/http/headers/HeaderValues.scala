package zhttp.http.headers

import io.netty.handler.codec.http.HttpHeaderValues

trait HeaderValues {
  final val `application/json`: CharSequence                  = HttpHeaderValues.APPLICATION_JSON
  final val `application/x-www-form-urlencoded`: CharSequence = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED
  final val `application/octet-stream`: CharSequence          = HttpHeaderValues.APPLICATION_OCTET_STREAM
  final val `application/xhtml+xml`: CharSequence             = HttpHeaderValues.APPLICATION_XHTML
  final val `application/xml`: CharSequence                   = HttpHeaderValues.APPLICATION_XML
  final val `application/zstd`: CharSequence                  = HttpHeaderValues.APPLICATION_ZSTD
  final val `attachment`: CharSequence                        = HttpHeaderValues.ATTACHMENT
  final val `base64`: CharSequence                            = HttpHeaderValues.BASE64
  final val `binary`: CharSequence                            = HttpHeaderValues.BINARY
  final val `boundary`: CharSequence                          = HttpHeaderValues.BOUNDARY
  final val `bytes`: CharSequence                             = HttpHeaderValues.BYTES
  final val `charset`: CharSequence                           = HttpHeaderValues.CHARSET
  final val `chunked`: CharSequence                           = HttpHeaderValues.CHUNKED
  final val `close`: CharSequence                             = HttpHeaderValues.CLOSE
  final val `compress`: CharSequence                          = HttpHeaderValues.COMPRESS
  final val `100-continue`: CharSequence                      = HttpHeaderValues.CONTINUE
  final val `deflate`: CharSequence                           = HttpHeaderValues.DEFLATE
  final val `x-deflate`: CharSequence                         = HttpHeaderValues.X_DEFLATE
  final val `file`: CharSequence                              = HttpHeaderValues.FILE
  final val `filename`: CharSequence                          = HttpHeaderValues.FILENAME
  final val `form-data`: CharSequence                         = HttpHeaderValues.FORM_DATA
  final val `gzip`: CharSequence                              = HttpHeaderValues.GZIP
  final val `br`: CharSequence                                = HttpHeaderValues.BR
  final val `zstd`: CharSequence                              = HttpHeaderValues.ZSTD
  final val `gzip,deflate`: CharSequence                      = HttpHeaderValues.GZIP_DEFLATE
  final val `x-gzip`: CharSequence                            = HttpHeaderValues.X_GZIP
  final val `identity`: CharSequence                          = HttpHeaderValues.IDENTITY
  final val `keep-alive`: CharSequence                        = HttpHeaderValues.KEEP_ALIVE
  final val `max-age`: CharSequence                           = HttpHeaderValues.MAX_AGE
  final val `max-stale`: CharSequence                         = HttpHeaderValues.MAX_STALE
  final val `min-fresh`: CharSequence                         = HttpHeaderValues.MIN_FRESH
  final val `multipart/form-data`: CharSequence               = HttpHeaderValues.MULTIPART_FORM_DATA
  final val `multipart/mixed`: CharSequence                   = HttpHeaderValues.MULTIPART_MIXED
  final val `must-revalidate`: CharSequence                   = HttpHeaderValues.MUST_REVALIDATE
  final val `name`: CharSequence                              = HttpHeaderValues.NAME
  final val `no-cache`: CharSequence                          = HttpHeaderValues.NO_CACHE
  final val `no-store`: CharSequence                          = HttpHeaderValues.NO_STORE
  final val `no-transform`: CharSequence                      = HttpHeaderValues.NO_TRANSFORM
  final val `none`: CharSequence                              = HttpHeaderValues.NONE
  final val `0`: CharSequence                                 = HttpHeaderValues.ZERO
  final val `only-if-cached`: CharSequence                    = HttpHeaderValues.ONLY_IF_CACHED
  final val `private`: CharSequence                           = HttpHeaderValues.PRIVATE
  final val `proxy-revalidate`: CharSequence                  = HttpHeaderValues.PROXY_REVALIDATE
  final val `public`: CharSequence                            = HttpHeaderValues.PUBLIC
  final val `quoted-printable`: CharSequence                  = HttpHeaderValues.QUOTED_PRINTABLE
  final val `s-maxage`: CharSequence                          = HttpHeaderValues.S_MAXAGE
  final val `text/css`: CharSequence                          = HttpHeaderValues.TEXT_CSS
  final val `text/html`: CharSequence                         = HttpHeaderValues.TEXT_HTML
  final val `text/event-stream`: CharSequence                 = HttpHeaderValues.TEXT_EVENT_STREAM
  final val `text/plain`: CharSequence                        = HttpHeaderValues.TEXT_PLAIN
  final val `trailers`: CharSequence                          = HttpHeaderValues.TRAILERS
  final val `upgrade`: CharSequence                           = HttpHeaderValues.UPGRADE
  final val `websocket`: CharSequence                         = HttpHeaderValues.WEBSOCKET
  final val `XMLHttpRequest`: CharSequence                    = HttpHeaderValues.XML_HTTP_REQUEST
}
