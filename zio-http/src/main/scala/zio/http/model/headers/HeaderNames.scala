package zio.http.model.headers

import io.netty.handler.codec.http.HttpHeaderNames
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * List of commonly use HeaderNames. They are provided to reduce bugs caused by
 * typos and also to improve performance. `HeaderNames` arent encoded everytime
 * one needs to send them over the wire.
 */
trait HeaderNames {
  final val accept: CharSequence                        = HttpHeaderNames.ACCEPT
  final val acceptEncoding: CharSequence                = HttpHeaderNames.ACCEPT_ENCODING
  final val acceptLanguage: CharSequence                = HttpHeaderNames.ACCEPT_LANGUAGE
  final val acceptRanges: CharSequence                  = HttpHeaderNames.ACCEPT_RANGES
  final val acceptPatch: CharSequence                   = HttpHeaderNames.ACCEPT_PATCH
  final val accessControlAllowCredentials: CharSequence = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS
  final val accessControlAllowHeaders: CharSequence     = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS
  final val accessControlAllowMethods: CharSequence     = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS
  final val accessControlAllowOrigin: CharSequence      = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN
  final val accessControlExposeHeaders: CharSequence    = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS
  final val accessControlMaxAge: CharSequence           = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE
  final val accessControlRequestHeaders: CharSequence   = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS
  final val accessControlRequestMethod: CharSequence    = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD
  final val age: CharSequence                           = HttpHeaderNames.AGE
  final val allow: CharSequence                         = HttpHeaderNames.ALLOW
  final val authorization: CharSequence                 = HttpHeaderNames.AUTHORIZATION
  final val cacheControl: CharSequence                  = HttpHeaderNames.CACHE_CONTROL
  final val connection: CharSequence                    = HttpHeaderNames.CONNECTION
  final val contentBase: CharSequence                   = HttpHeaderNames.CONTENT_BASE
  final val contentEncoding: CharSequence               = HttpHeaderNames.CONTENT_ENCODING
  final val contentLanguage: CharSequence               = HttpHeaderNames.CONTENT_LANGUAGE
  final val contentLength: CharSequence                 = HttpHeaderNames.CONTENT_LENGTH
  final val contentLocation: CharSequence               = HttpHeaderNames.CONTENT_LOCATION
  final val contentTransferEncoding: CharSequence       = HttpHeaderNames.CONTENT_TRANSFER_ENCODING
  final val contentDisposition: CharSequence            = HttpHeaderNames.CONTENT_DISPOSITION
  final val contentMd5: CharSequence                    = HttpHeaderNames.CONTENT_MD5
  final val contentRange: CharSequence                  = HttpHeaderNames.CONTENT_RANGE
  final val contentSecurityPolicy: CharSequence         = HttpHeaderNames.CONTENT_SECURITY_POLICY
  final val contentType: CharSequence                   = HttpHeaderNames.CONTENT_TYPE
  final val cookie: CharSequence                        = HttpHeaderNames.COOKIE
  final val date: CharSequence                          = HttpHeaderNames.DATE
  final val dnt: CharSequence                           = HttpHeaderNames.DNT
  final val etag: CharSequence                          = HttpHeaderNames.ETAG
  final val expect: CharSequence                        = HttpHeaderNames.EXPECT
  final val expires: CharSequence                       = HttpHeaderNames.EXPIRES
  final val from: CharSequence                          = HttpHeaderNames.FROM
  final val host: CharSequence                          = HttpHeaderNames.HOST
  final val ifMatch: CharSequence                       = HttpHeaderNames.IF_MATCH
  final val ifModifiedSince: CharSequence               = HttpHeaderNames.IF_MODIFIED_SINCE
  final val ifNoneMatch: CharSequence                   = HttpHeaderNames.IF_NONE_MATCH
  final val ifRange: CharSequence                       = HttpHeaderNames.IF_RANGE
  final val ifUnmodifiedSince: CharSequence             = HttpHeaderNames.IF_UNMODIFIED_SINCE
  final val lastModified: CharSequence                  = HttpHeaderNames.LAST_MODIFIED
  final val location: CharSequence                      = HttpHeaderNames.LOCATION
  final val maxForwards: CharSequence                   = HttpHeaderNames.MAX_FORWARDS
  final val origin: CharSequence                        = HttpHeaderNames.ORIGIN
  final val pragma: CharSequence                        = HttpHeaderNames.PRAGMA
  final val proxyAuthenticate: CharSequence             = HttpHeaderNames.PROXY_AUTHENTICATE
  final val proxyAuthorization: CharSequence            = HttpHeaderNames.PROXY_AUTHORIZATION
  final val range: CharSequence                         = HttpHeaderNames.RANGE
  final val referer: CharSequence                       = HttpHeaderNames.REFERER
  final val retryAfter: CharSequence                    = HttpHeaderNames.RETRY_AFTER
  final val secWebSocketLocation: CharSequence          = HttpHeaderNames.SEC_WEBSOCKET_LOCATION
  final val secWebSocketOrigin: CharSequence            = HttpHeaderNames.SEC_WEBSOCKET_ORIGIN
  final val secWebSocketProtocol: CharSequence          = HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL
  final val secWebSocketVersion: CharSequence           = HttpHeaderNames.SEC_WEBSOCKET_VERSION
  final val secWebSocketKey: CharSequence               = HttpHeaderNames.SEC_WEBSOCKET_KEY
  final val secWebSocketAccept: CharSequence            = HttpHeaderNames.SEC_WEBSOCKET_ACCEPT
  final val secWebSocketExtensions: CharSequence        = HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS
  final val server: CharSequence                        = HttpHeaderNames.SERVER
  final val setCookie: CharSequence                     = HttpHeaderNames.SET_COOKIE
  final val te: CharSequence                            = HttpHeaderNames.TE
  final val trailer: CharSequence                       = HttpHeaderNames.TRAILER
  final val transferEncoding: CharSequence              = HttpHeaderNames.TRANSFER_ENCODING
  final val upgrade: CharSequence                       = HttpHeaderNames.UPGRADE
  final val upgradeInsecureRequests: CharSequence       = HttpHeaderNames.UPGRADE_INSECURE_REQUESTS
  final val userAgent: CharSequence                     = HttpHeaderNames.USER_AGENT
  final val vary: CharSequence                          = HttpHeaderNames.VARY
  final val via: CharSequence                           = HttpHeaderNames.VIA
  final val warning: CharSequence                       = HttpHeaderNames.WARNING
  final val webSocketLocation: CharSequence             = HttpHeaderNames.WEBSOCKET_LOCATION
  final val webSocketOrigin: CharSequence               = HttpHeaderNames.WEBSOCKET_ORIGIN
  final val webSocketProtocol: CharSequence             = HttpHeaderNames.WEBSOCKET_PROTOCOL
  final val wwwAuthenticate: CharSequence               = HttpHeaderNames.WWW_AUTHENTICATE
  final val xFrameOptions: CharSequence                 = HttpHeaderNames.X_FRAME_OPTIONS
  final val xRequestedWith: CharSequence                = HttpHeaderNames.X_REQUESTED_WITH
}
