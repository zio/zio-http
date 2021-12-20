package zhttp.http

import io.netty.handler.codec.http.HttpHeaderValues

object HeaderValue {
  val `application/json`: CharSequence                  = HttpHeaderValues.APPLICATION_JSON
  val `application/x-www-form-urlencoded`: CharSequence = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED
  val `application/octet-stream`: CharSequence          = HttpHeaderValues.APPLICATION_OCTET_STREAM
  val `application/xhtml+xml`: CharSequence             = HttpHeaderValues.APPLICATION_XHTML
  val `application/xml`: CharSequence                   = HttpHeaderValues.APPLICATION_XML
  val `application/zstd`: CharSequence                  = HttpHeaderValues.APPLICATION_ZSTD
  val `attachment`: CharSequence                        = HttpHeaderValues.ATTACHMENT
  val `base64`: CharSequence                            = HttpHeaderValues.BASE64
  val `binary`: CharSequence                            = HttpHeaderValues.BINARY
  val `boundary`: CharSequence                          = HttpHeaderValues.BOUNDARY
  val `bytes`: CharSequence                             = HttpHeaderValues.BYTES
  val `charset`: CharSequence                           = HttpHeaderValues.CHARSET
  val `chunked`: CharSequence                           = HttpHeaderValues.CHUNKED
  val `close`: CharSequence                             = HttpHeaderValues.CLOSE
  val `compress`: CharSequence                          = HttpHeaderValues.COMPRESS
  val `100-continue`: CharSequence                      = HttpHeaderValues.CONTINUE
  val `deflate`: CharSequence                           = HttpHeaderValues.DEFLATE
  val `x-deflate`: CharSequence                         = HttpHeaderValues.X_DEFLATE
  val `file`: CharSequence                              = HttpHeaderValues.FILE
  val `filename`: CharSequence                          = HttpHeaderValues.FILENAME
  val `form-data`: CharSequence                         = HttpHeaderValues.FORM_DATA
  val `gzip`: CharSequence                              = HttpHeaderValues.GZIP
  val `br`: CharSequence                                = HttpHeaderValues.BR
  val `zstd`: CharSequence                              = HttpHeaderValues.ZSTD
  val `gzip,deflate`: CharSequence                      = HttpHeaderValues.GZIP_DEFLATE
  val `x-gzip`: CharSequence                            = HttpHeaderValues.X_GZIP
  val `identity`: CharSequence                          = HttpHeaderValues.IDENTITY
  val `keep-alive`: CharSequence                        = HttpHeaderValues.KEEP_ALIVE
  val `max-age`: CharSequence                           = HttpHeaderValues.MAX_AGE
  val `max-stale`: CharSequence                         = HttpHeaderValues.MAX_STALE
  val `min-fresh`: CharSequence                         = HttpHeaderValues.MIN_FRESH
  val `multipart/form-data`: CharSequence               = HttpHeaderValues.MULTIPART_FORM_DATA
  val `multipart/mixed`: CharSequence                   = HttpHeaderValues.MULTIPART_MIXED
  val `must-revalidate`: CharSequence                   = HttpHeaderValues.MUST_REVALIDATE
  val `name`: CharSequence                              = HttpHeaderValues.NAME
  val `no-cache`: CharSequence                          = HttpHeaderValues.NO_CACHE
  val `no-store`: CharSequence                          = HttpHeaderValues.NO_STORE
  val `no-transform`: CharSequence                      = HttpHeaderValues.NO_TRANSFORM
  val `none`: CharSequence                              = HttpHeaderValues.NONE
  val `0`: CharSequence                                 = HttpHeaderValues.ZERO
  val `only-if-cached`: CharSequence                    = HttpHeaderValues.ONLY_IF_CACHED
  val `private`: CharSequence                           = HttpHeaderValues.PRIVATE
  val `proxy-revalidate`: CharSequence                  = HttpHeaderValues.PROXY_REVALIDATE
  val `public`: CharSequence                            = HttpHeaderValues.PUBLIC
  val `quoted-printable`: CharSequence                  = HttpHeaderValues.QUOTED_PRINTABLE
  val `s-maxage`: CharSequence                          = HttpHeaderValues.S_MAXAGE
  val `text/css`: CharSequence                          = HttpHeaderValues.TEXT_CSS
  val `text/html`: CharSequence                         = HttpHeaderValues.TEXT_HTML
  val `text/event-stream`: CharSequence                 = HttpHeaderValues.TEXT_EVENT_STREAM
  val `text/plain`: CharSequence                        = HttpHeaderValues.TEXT_PLAIN
  val `trailers`: CharSequence                          = HttpHeaderValues.TRAILERS
  val `upgrade`: CharSequence                           = HttpHeaderValues.UPGRADE
  val `websocket`: CharSequence                         = HttpHeaderValues.WEBSOCKET
  val `XMLHttpRequest`: CharSequence                    = HttpHeaderValues.XML_HTTP_REQUEST
}
