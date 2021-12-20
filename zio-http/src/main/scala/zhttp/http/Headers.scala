package zhttp.http

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaderNames, HttpHeaderValues, HttpHeaders}
import zhttp.http.HeaderExtension.BasicSchemeName
import zhttp.http.Headers.Names.H
import zio.Chunk

import java.util.Base64
import scala.jdk.CollectionConverters._

/**
 * TODO: use Chunk instead of List TODO: use Tuple2 instead of Header
 */

final case class Headers(toChunk: Chunk[Header]) extends HeaderExtension[Headers] {
  self =>

  def ++(other: Headers): Headers = self.combine(other)

  def combine(other: Headers): Headers = Headers(self.toChunk ++ other.toChunk)

  def combineIf(cond: Boolean)(other: Headers): Headers = if (cond) Headers(self.toChunk ++ other.toChunk) else self

  override def getHeaders: Headers = self

  def toList: List[Header] = toChunk.toList

  override def updateHeaders(f: Headers => Headers): Headers = f(self)

  def when(cond: Boolean): Headers = if (cond) self else Headers.empty

  /**
   * Converts a Headers to [io.netty.handler.codec.http.HttpHeaders
   */
  private[zhttp] def encode: HttpHeaders =
    self.toList.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { case (headers, entry) =>
      headers.set(entry._1, entry._2)
    }
}

object Headers {

  def apply(name: CharSequence, value: CharSequence): Headers = Headers(Chunk((name, value)))

  def apply(tuples: Header*): Headers = Headers(Chunk.fromIterable(tuples))

  def apply(iter: Iterable[Header]): Headers = Headers(Chunk.fromIterable(iter))

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def make(headers: HttpHeaders): Headers = Headers {
    headers
      .iteratorCharSequence()
      .asScala
      .map(h => (h.getKey, h.getValue))
      .toList
  }

  def makeAccept(value: CharSequence): Headers = Headers(H.`accept`, value)

  def makeAcceptCharset(value: CharSequence): Headers = Headers(H.`accept-charset`, value)

  def makeAcceptEncoding(value: CharSequence): Headers = Headers(H.`accept-encoding`, value)

  def makeAcceptLanguage(value: CharSequence): Headers = Headers(H.`accept-language`, value)

  def makeAcceptPatch(value: CharSequence): Headers = Headers(H.`accept-patch`, value)

  def makeAcceptRanges(value: CharSequence): Headers = Headers(H.`accept-ranges`, value)

  def makeAccessControlAllowCredentials(value: Boolean): Headers =
    Headers(H.`access-control-allow-credentials`, value.toString)

  def makeAccessControlAllowHeaders(value: CharSequence): Headers = Headers(H.`access-control-allow-headers`, value)

  def makeAccessControlAllowMethods(methods: Method*): Headers =
    Headers(H.`access-control-allow-methods`, methods.map(_.toString()).mkString(", "))

  def makeAccessControlAllowOrigin(value: CharSequence): Headers = Headers(H.`access-control-allow-origin`, value)

  def makeAccessControlExposeHeaders(value: CharSequence): Headers = Headers(H.`access-control-expose-headers`, value)

  def makeAccessControlMaxAge(value: CharSequence): Headers = Headers(H.`access-control-max-age`, value)

  def makeAccessControlRequestHeaders(value: CharSequence): Headers = Headers(H.`access-control-request-headers`, value)

  def makeAccessControlRequestMethod(method: Method): Headers =
    Headers(H.`access-control-request-method`, method.asHttpMethod.name())

  def makeAge(value: CharSequence): Headers = Headers(H.`age`, value)

  def makeAllow(value: CharSequence): Headers = Headers(H.`allow`, value)

  def makeAuthorization(value: CharSequence): Headers = Headers(H.`authorization`, value)

  def makeBasicAuthorizationHeader(username: String, password: String): Headers = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    Headers(HttpHeaderNames.AUTHORIZATION, value)
  }

  def makeCacheControl(value: CharSequence): Headers = Headers(H.`cache-control`, value)

  def makeConnection(value: CharSequence): Headers = Headers(H.`connection`, value)

  def makeContentBase(value: CharSequence): Headers = Headers(H.`content-base`, value)

  def makeContentDisposition(value: CharSequence): Headers = Headers(H.`content-disposition`, value)

  def makeContentEncoding(value: CharSequence): Headers = Headers(H.`content-encoding`, value)

  def makeContentLanguage(value: CharSequence): Headers = Headers(H.`content-language`, value)

  def makeContentLength(value: Long): Headers = Headers(H.`content-length`, value.toString)

  def makeContentLocation(value: CharSequence): Headers = Headers(H.`content-location`, value)

  def makeContentMd5(value: CharSequence): Headers = Headers(H.`content-md5`, value)

  def makeContentRange(value: CharSequence): Headers = Headers(H.`content-range`, value)

  def makeContentSecurityPolicy(value: CharSequence): Headers = Headers(H.`content-security-policy`, value)

  def makeContentTransferEncoding(value: CharSequence): Headers = Headers(H.`content-transfer-encoding`, value)

  def makeContentType(value: CharSequence): Headers = Headers(H.`content-type`, value)

  def makeCookie(value: CharSequence): Headers = Headers(H.`cookie`, value)

  def makeDate(value: CharSequence): Headers = Headers(H.`date`, value)

  def makeDnt(value: CharSequence): Headers = Headers(H.`dnt`, value)

  def makeEtag(value: CharSequence): Headers = Headers(H.`etag`, value)

  def makeExpect(value: CharSequence): Headers = Headers(H.`expect`, value)

  def makeExpires(value: CharSequence): Headers = Headers(H.`expires`, value)

  def makeFrom(value: CharSequence): Headers = Headers(H.`from`, value)

  def makeHost(value: CharSequence): Headers = Headers(H.`host`, value)

  def makeIfMatch(value: CharSequence): Headers = Headers(H.`if-match`, value)

  def makeIfModifiedSince(value: CharSequence): Headers = Headers(H.`if-modified-since`, value)

  def makeIfNoneMatch(value: CharSequence): Headers = Headers(H.`if-none-match`, value)

  def makeIfRange(value: CharSequence): Headers = Headers(H.`if-range`, value)

  def makeIfUnmodifiedSince(value: CharSequence): Headers = Headers(H.`if-unmodified-since`, value)

  def makeLastModified(value: CharSequence): Headers = Headers(H.`last-modified`, value)

  def makeLocation(value: CharSequence): Headers = Headers(H.`location`, value)

  def makeMaxForwards(value: CharSequence): Headers = Headers(H.`max-forwards`, value)

  def makeOrigin(value: CharSequence): Headers = Headers(H.`origin`, value)

  def makePragma(value: CharSequence): Headers = Headers(H.`pragma`, value)

  def makeProxyAuthenticate(value: CharSequence): Headers = Headers(H.`proxy-authenticate`, value)

  def makeProxyAuthorization(value: CharSequence): Headers = Headers(H.`proxy-authorization`, value)

  def makeRange(value: CharSequence): Headers = Headers(H.`range`, value)

  def makeReferer(value: CharSequence): Headers = Headers(H.`referer`, value)

  def makeRetryAfter(value: CharSequence): Headers = Headers(H.`retry-after`, value)

  def makeSecWebSocketAccept(value: CharSequence): Headers = Headers(H.`sec-websocket-accept`, value)

  def makeSecWebSocketExtensions(value: CharSequence): Headers = Headers(H.`sec-websocket-extensions`, value)

  def makeSecWebSocketKey(value: CharSequence): Headers = Headers(H.`sec-websocket-key`, value)

  def makeSecWebSocketLocation(value: CharSequence): Headers = Headers(H.`sec-websocket-location`, value)

  def makeSecWebSocketOrigin(value: CharSequence): Headers = Headers(H.`sec-websocket-origin`, value)

  def makeSecWebSocketProtocol(value: CharSequence): Headers = Headers(H.`sec-websocket-protocol`, value)

  def makeSecWebSocketVersion(value: CharSequence): Headers = Headers(H.`sec-websocket-version`, value)

  def makeServer(value: CharSequence): Headers = Headers(H.`server`, value)

  def makeSetCookie(value: Cookie): Headers = Headers(H.`set-cookie`, value.encode)

  def makeTe(value: CharSequence): Headers = Headers(H.`te`, value)

  def makeTrailer(value: CharSequence): Headers = Headers(H.`trailer`, value)

  def makeTransferEncoding(value: CharSequence): Headers = Headers(H.`transfer-encoding`, value)

  def makeUpgrade(value: CharSequence): Headers = Headers(H.`upgrade`, value)

  def makeUpgradeInsecureRequests(value: CharSequence): Headers = Headers(H.`upgrade-insecure-requests`, value)

  def makeUserAgent(value: CharSequence): Headers = Headers(H.`user-agent`, value)

  def makeVary(value: CharSequence): Headers = Headers(H.`vary`, value)

  def makeVia(value: CharSequence): Headers = Headers(H.`via`, value)

  def makeWarning(value: CharSequence): Headers = Headers(H.`warning`, value)

  def makeWebSocketLocation(value: CharSequence): Headers = Headers(H.`websocket-location`, value)

  def makeWebSocketOrigin(value: CharSequence): Headers = Headers(H.`websocket-origin`, value)

  def makeWebSocketProtocol(value: CharSequence): Headers = Headers(H.`websocket-protocol`, value)

  def makeWwwAuthenticate(value: CharSequence): Headers = Headers(H.`www-authenticate`, value)

  def makeXFrameOptions(value: CharSequence): Headers = Headers(H.`x-frame-options`, value)

  def makeXRequestedWith(value: CharSequence): Headers = Headers(H.`x-requested-with`, value)

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else Headers.empty

  val empty: Headers = Headers(Nil)

  private[zhttp] def decode(headers: HttpHeaders): Headers =
    Headers(headers.entries().asScala.toList.map(entry => (entry.getKey, entry.getValue)))

  object Names {
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

    object H {
      val `accept`: CharSequence                           = HttpHeaderNames.ACCEPT
      val `accept-charset`: CharSequence                   = HttpHeaderNames.ACCEPT_CHARSET
      val `accept-encoding`: CharSequence                  = HttpHeaderNames.ACCEPT_ENCODING
      val `accept-language`: CharSequence                  = HttpHeaderNames.ACCEPT_LANGUAGE
      val `accept-ranges`: CharSequence                    = HttpHeaderNames.ACCEPT_RANGES
      val `accept-patch`: CharSequence                     = HttpHeaderNames.ACCEPT_PATCH
      val `access-control-allow-credentials`: CharSequence = HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS
      val `access-control-allow-headers`: CharSequence     = HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS
      val `access-control-allow-methods`: CharSequence     = HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS
      val `access-control-allow-origin`: CharSequence      = HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN
      val `access-control-expose-headers`: CharSequence    = HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS
      val `access-control-max-age`: CharSequence           = HttpHeaderNames.ACCESS_CONTROL_MAX_AGE
      val `access-control-request-headers`: CharSequence   = HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS
      val `access-control-request-method`: CharSequence    = HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD
      val `age`: CharSequence                              = HttpHeaderNames.AGE
      val `allow`: CharSequence                            = HttpHeaderNames.ALLOW
      val `authorization`: CharSequence                    = HttpHeaderNames.AUTHORIZATION
      val `cache-control`: CharSequence                    = HttpHeaderNames.CACHE_CONTROL
      val `connection`: CharSequence                       = HttpHeaderNames.CONNECTION
      val `content-base`: CharSequence                     = HttpHeaderNames.CONTENT_BASE
      val `content-encoding`: CharSequence                 = HttpHeaderNames.CONTENT_ENCODING
      val `content-language`: CharSequence                 = HttpHeaderNames.CONTENT_LANGUAGE
      val `content-length`: CharSequence                   = HttpHeaderNames.CONTENT_LENGTH
      val `content-location`: CharSequence                 = HttpHeaderNames.CONTENT_LOCATION
      val `content-transfer-encoding`: CharSequence        = HttpHeaderNames.CONTENT_TRANSFER_ENCODING
      val `content-disposition`: CharSequence              = HttpHeaderNames.CONTENT_DISPOSITION
      val `content-md5`: CharSequence                      = HttpHeaderNames.CONTENT_MD5
      val `content-range`: CharSequence                    = HttpHeaderNames.CONTENT_RANGE
      val `content-security-policy`: CharSequence          = HttpHeaderNames.CONTENT_SECURITY_POLICY
      val `content-type`: CharSequence                     = HttpHeaderNames.CONTENT_TYPE
      val `cookie`: CharSequence                           = HttpHeaderNames.COOKIE
      val `date`: CharSequence                             = HttpHeaderNames.DATE
      val `dnt`: CharSequence                              = HttpHeaderNames.DNT
      val `etag`: CharSequence                             = HttpHeaderNames.ETAG
      val `expect`: CharSequence                           = HttpHeaderNames.EXPECT
      val `expires`: CharSequence                          = HttpHeaderNames.EXPIRES
      val `from`: CharSequence                             = HttpHeaderNames.FROM
      val `host`: CharSequence                             = HttpHeaderNames.HOST
      val `if-match`: CharSequence                         = HttpHeaderNames.IF_MATCH
      val `if-modified-since`: CharSequence                = HttpHeaderNames.IF_MODIFIED_SINCE
      val `if-none-match`: CharSequence                    = HttpHeaderNames.IF_NONE_MATCH
      val `if-range`: CharSequence                         = HttpHeaderNames.IF_RANGE
      val `if-unmodified-since`: CharSequence              = HttpHeaderNames.IF_UNMODIFIED_SINCE
      val `last-modified`: CharSequence                    = HttpHeaderNames.LAST_MODIFIED
      val `location`: CharSequence                         = HttpHeaderNames.LOCATION
      val `max-forwards`: CharSequence                     = HttpHeaderNames.MAX_FORWARDS
      val `origin`: CharSequence                           = HttpHeaderNames.ORIGIN
      val `pragma`: CharSequence                           = HttpHeaderNames.PRAGMA
      val `proxy-authenticate`: CharSequence               = HttpHeaderNames.PROXY_AUTHENTICATE
      val `proxy-authorization`: CharSequence              = HttpHeaderNames.PROXY_AUTHORIZATION
      val `range`: CharSequence                            = HttpHeaderNames.RANGE
      val `referer`: CharSequence                          = HttpHeaderNames.REFERER
      val `retry-after`: CharSequence                      = HttpHeaderNames.RETRY_AFTER
      val `sec-websocket-key1`: CharSequence               = HttpHeaderNames.SEC_WEBSOCKET_KEY1
      val `sec-websocket-key2`: CharSequence               = HttpHeaderNames.SEC_WEBSOCKET_KEY2
      val `sec-websocket-location`: CharSequence           = HttpHeaderNames.SEC_WEBSOCKET_LOCATION
      val `sec-websocket-origin`: CharSequence             = HttpHeaderNames.SEC_WEBSOCKET_ORIGIN
      val `sec-websocket-protocol`: CharSequence           = HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL
      val `sec-websocket-version`: CharSequence            = HttpHeaderNames.SEC_WEBSOCKET_VERSION
      val `sec-websocket-key`: CharSequence                = HttpHeaderNames.SEC_WEBSOCKET_KEY
      val `sec-websocket-accept`: CharSequence             = HttpHeaderNames.SEC_WEBSOCKET_ACCEPT
      val `sec-websocket-extensions`: CharSequence         = HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS
      val `server`: CharSequence                           = HttpHeaderNames.SERVER
      val `set-cookie`: CharSequence                       = HttpHeaderNames.SET_COOKIE
      val `set-cookie2`: CharSequence                      = HttpHeaderNames.SET_COOKIE2
      val `te`: CharSequence                               = HttpHeaderNames.TE
      val `trailer`: CharSequence                          = HttpHeaderNames.TRAILER
      val `transfer-encoding`: CharSequence                = HttpHeaderNames.TRANSFER_ENCODING
      val `upgrade`: CharSequence                          = HttpHeaderNames.UPGRADE
      val `upgrade-insecure-requests`: CharSequence        = HttpHeaderNames.UPGRADE_INSECURE_REQUESTS
      val `user-agent`: CharSequence                       = HttpHeaderNames.USER_AGENT
      val `vary`: CharSequence                             = HttpHeaderNames.VARY
      val `via`: CharSequence                              = HttpHeaderNames.VIA
      val `warning`: CharSequence                          = HttpHeaderNames.WARNING
      val `websocket-location`: CharSequence               = HttpHeaderNames.WEBSOCKET_LOCATION
      val `websocket-origin`: CharSequence                 = HttpHeaderNames.WEBSOCKET_ORIGIN
      val `websocket-protocol`: CharSequence               = HttpHeaderNames.WEBSOCKET_PROTOCOL
      val `www-authenticate`: CharSequence                 = HttpHeaderNames.WWW_AUTHENTICATE
      val `x-frame-options`: CharSequence                  = HttpHeaderNames.X_FRAME_OPTIONS
      val `x-requested-with`: CharSequence                 = HttpHeaderNames.X_REQUESTED_WITH
    }
  }
}
