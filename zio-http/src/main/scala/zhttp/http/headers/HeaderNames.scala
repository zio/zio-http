package zhttp.http.headers

import io.netty.handler.codec.http.HttpHeaderNames

/**
 * List of commonly use HeaderNames. They are provided to reduce bugs caused by typos and also to improve performance.
 * `HeaderNames` arent encoded everytime one needs to send them over the wire.
 */
trait HeaderNames {
  final val Accept: CharSequence                        = HttpHeaderNames.ACCEPT
  final val AcceptCharset: CharSequence                 = HttpHeaderNames.ACCEPT_CHARSET
  final val AcceptEncoding: CharSequence                = HttpHeaderNames.ACCEPT_ENCODING
  final val AcceptLanguage: CharSequence                = HttpHeaderNames.ACCEPT_LANGUAGE
  final val AcceptRanges: CharSequence                  = HttpHeaderNames.ACCEPT_RANGES
  final val AcceptPatch: CharSequence                   = HttpHeaderNames.ACCEPT_PATCH
  final val AccessControlAllowCredentials: CharSequence = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS
  final val AccessControlAllowHeaders: CharSequence     = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS
  final val AccessControlAllowMethods: CharSequence     = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS
  final val AccessControlAllowOrigin: CharSequence      = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN
  final val AccessControlExposeHeaders: CharSequence    = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS
  final val AccessControlMaxAge: CharSequence           = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE
  final val AccessControlRequestHeaders: CharSequence   = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS
  final val AccessControlRequestMethod: CharSequence    = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD
  final val Age: CharSequence                           = HttpHeaderNames.AGE
  final val Allow: CharSequence                         = HttpHeaderNames.ALLOW
  final val Authorization: CharSequence                 = HttpHeaderNames.AUTHORIZATION
  final val CacheControl: CharSequence                  = HttpHeaderNames.CACHE_CONTROL
  final val Connection: CharSequence                    = HttpHeaderNames.CONNECTION
  final val ContentBase: CharSequence                   = HttpHeaderNames.CONTENT_BASE
  final val ContentEncoding: CharSequence               = HttpHeaderNames.CONTENT_ENCODING
  final val ContentLanguage: CharSequence               = HttpHeaderNames.CONTENT_LANGUAGE
  final val ContentLength: CharSequence                 = HttpHeaderNames.CONTENT_LENGTH
  final val ContentLocation: CharSequence               = HttpHeaderNames.CONTENT_LOCATION
  final val ContentTransferEncoding: CharSequence       = HttpHeaderNames.CONTENT_TRANSFER_ENCODING
  final val ContentDisposition: CharSequence            = HttpHeaderNames.CONTENT_DISPOSITION
  final val ContentMd5: CharSequence                    = HttpHeaderNames.CONTENT_MD5
  final val ContentRange: CharSequence                  = HttpHeaderNames.CONTENT_RANGE
  final val ContentSecurityPolicy: CharSequence         = HttpHeaderNames.CONTENT_SECURITY_POLICY
  final val ContentType: CharSequence                   = HttpHeaderNames.CONTENT_TYPE
  final val Cookie: CharSequence                        = HttpHeaderNames.COOKIE
  final val Date: CharSequence                          = HttpHeaderNames.DATE
  final val Dnt: CharSequence                           = HttpHeaderNames.DNT
  final val Etag: CharSequence                          = HttpHeaderNames.ETAG
  final val Expect: CharSequence                        = HttpHeaderNames.EXPECT
  final val Expires: CharSequence                       = HttpHeaderNames.EXPIRES
  final val From: CharSequence                          = HttpHeaderNames.FROM
  final val Host: CharSequence                          = HttpHeaderNames.HOST
  final val IfMatch: CharSequence                       = HttpHeaderNames.IF_MATCH
  final val IfModifiedSince: CharSequence               = HttpHeaderNames.IF_MODIFIED_SINCE
  final val IfNoneMatch: CharSequence                   = HttpHeaderNames.IF_NONE_MATCH
  final val IfRange: CharSequence                       = HttpHeaderNames.IF_RANGE
  final val IfUnmodifiedSince: CharSequence             = HttpHeaderNames.IF_UNMODIFIED_SINCE
  final val LastModified: CharSequence                  = HttpHeaderNames.LAST_MODIFIED
  final val Location: CharSequence                      = HttpHeaderNames.LOCATION
  final val MaxForwards: CharSequence                   = HttpHeaderNames.MAX_FORWARDS
  final val Origin: CharSequence                        = HttpHeaderNames.ORIGIN
  final val Pragma: CharSequence                        = HttpHeaderNames.PRAGMA
  final val ProxyAuthenticate: CharSequence             = HttpHeaderNames.PROXY_AUTHENTICATE
  final val ProxyAuthorization: CharSequence            = HttpHeaderNames.PROXY_AUTHORIZATION
  final val Range: CharSequence                         = HttpHeaderNames.RANGE
  final val Referer: CharSequence                       = HttpHeaderNames.REFERER
  final val RetryAfter: CharSequence                    = HttpHeaderNames.RETRY_AFTER
  final val SecWebSocketLocation: CharSequence          = HttpHeaderNames.SEC_WEBSOCKET_LOCATION
  final val SecWebSocketOrigin: CharSequence            = HttpHeaderNames.SEC_WEBSOCKET_ORIGIN
  final val SecWebSocketProtocol: CharSequence          = HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL
  final val SecWebSocketVersion: CharSequence           = HttpHeaderNames.SEC_WEBSOCKET_VERSION
  final val SecWebSocketKey: CharSequence               = HttpHeaderNames.SEC_WEBSOCKET_KEY
  final val SecWebSocketAccept: CharSequence            = HttpHeaderNames.SEC_WEBSOCKET_ACCEPT
  final val SecWebSocketExtensions: CharSequence        = HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS
  final val Server: CharSequence                        = HttpHeaderNames.SERVER
  final val SetCookie: CharSequence                     = HttpHeaderNames.SET_COOKIE
  final val Te: CharSequence                            = HttpHeaderNames.TE
  final val Trailer: CharSequence                       = HttpHeaderNames.TRAILER
  final val TransferEncoding: CharSequence              = HttpHeaderNames.TRANSFER_ENCODING
  final val Upgrade: CharSequence                       = HttpHeaderNames.UPGRADE
  final val UpgradeInsecureRequests: CharSequence       = HttpHeaderNames.UPGRADE_INSECURE_REQUESTS
  final val UserAgent: CharSequence                     = HttpHeaderNames.USER_AGENT
  final val Vary: CharSequence                          = HttpHeaderNames.VARY
  final val Via: CharSequence                           = HttpHeaderNames.VIA
  final val Warning: CharSequence                       = HttpHeaderNames.WARNING
  final val WebSocketLocation: CharSequence             = HttpHeaderNames.WEBSOCKET_LOCATION
  final val WebSocketOrigin: CharSequence               = HttpHeaderNames.WEBSOCKET_ORIGIN
  final val WebSocketProtocol: CharSequence             = HttpHeaderNames.WEBSOCKET_PROTOCOL
  final val WwwAuthenticate: CharSequence               = HttpHeaderNames.WWW_AUTHENTICATE
  final val XFrameOptions: CharSequence                 = HttpHeaderNames.X_FRAME_OPTIONS
  final val XRequestedWith: CharSequence                = HttpHeaderNames.X_REQUESTED_WITH
}
