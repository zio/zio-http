package zio.http.model.headers

import zio.http.{Request, Response, URL}
import zio.{Chunk, Duration, durationInt}
import zio.http.model.{Cookie, MediaType, Method}

import java.net.URI
import java.time.{ZoneId, ZonedDateTime}

object HeaderTypedValues {

  /** Accept header value. */
  sealed trait Accept

  object Accept {

    /**
     * The Accept header value one or more MIME types optionally weighed with
     * quality factor.
     */
    final case class AcceptValue(mimeTypes: Chunk[MediaTypeWithQFactor]) extends Accept

    final case class MediaTypeWithQFactor(mediaType: MediaType, qFactor: Option[Double])

    /** The Accept header value is invalid. */
    case object InvalidAcceptValue extends Accept
  }

  /**
   * Represents an AcceptEncoding header value.
   */
  sealed trait AcceptEncoding {
    val raw: String
  }

  object AcceptEncoding {

    /**
     * Signals an invalid value present in the header value.
     */
    case object InvalidEncoding extends AcceptEncoding {
      override val raw: String = "Invalid header value"
    }

    /**
     * A compression format that uses the Brotli algorithm.
     */
    final case class BrEncoding(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "br"
    }

    /**
     * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
     */
    final case class CompressEncoding(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "compress"
    }

    /**
     * A compression format that uses the zlib structure with the deflate
     * compression algorithm.
     */
    final case class DeflateEncoding(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "deflate"
    }

    /**
     * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
     * CRC.
     */
    final case class GZipEncoding(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "gzip"
    }

    /**
     * Indicates the identity function (that is, without modification or
     * compression). This value is always considered as acceptable, even if
     * omitted.
     */
    final case class IdentityEncoding(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "identity"
    }

    /**
     * Maintains a chunk of AcceptEncoding values.
     */
    final case class MultipleEncodings(encodings: Chunk[AcceptEncoding]) extends AcceptEncoding {
      override val raw: String = encodings.map(_.raw).mkString(",")
    }

    /**
     * Matches any content encoding not already listed in the header. This is
     * the default value if the header is not present.
     */
    final case class NoPreferenceEncoding(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "*"
    }
  }

  /**
   * The Accept-Language request HTTP header indicates the natural language and
   * locale that the client prefers.
   */
  sealed trait AcceptLanguage

  object AcceptLanguage {

    case class AcceptedLanguage(language: String, weight: Option[Double]) extends AcceptLanguage

    case class AcceptedLanguages(languages: Chunk[AcceptLanguage]) extends AcceptLanguage

    case object AnyLanguage extends AcceptLanguage

    case object InvalidAcceptLanguageValue extends AcceptLanguage
  }

  /**
   * The Accept-Patch response HTTP header advertises which media-type the
   * server is able to understand in a PATCH request.
   */
  sealed trait AcceptPatch

  object AcceptPatch {

    case class AcceptPatchValue(mediaTypes: Chunk[MediaType]) extends AcceptPatch

    case object InvalidAcceptPatchValue extends AcceptPatch
  }

  /**
   * The Accept-Ranges HTTP response header is a marker used by the server to
   * advertise its support for partial requests from the client for file
   * downloads. The value of this field indicates the unit that can be used to
   * define a range. By default the RFC 7233 specification supports only 2
   * possible values.
   */
  sealed trait AcceptRanges {
    val name: String
  }

  object AcceptRanges {
    case object Bytes extends AcceptRanges {
      override val name = "bytes"
    }

    case object None extends AcceptRanges {
      override val name = "none"
    }

    case object InvalidAcceptRanges extends AcceptRanges {
      override val name = ""
    }
  }

  sealed trait AccessControlAllowCredentials

  object AccessControlAllowCredentials {

    /**
     * The Access-Control-Allow-Credentials header is sent in response to a
     * preflight request which includes the Access-Control-Request-Headers to
     * indicate whether or not the actual request can be made using credentials.
     */
    case object AllowCredentials extends AccessControlAllowCredentials

    /**
     * The Access-Control-Allow-Credentials header is not sent in response to a
     * preflight request.
     */
    case object DoNotAllowCredentials extends AccessControlAllowCredentials

  }

  sealed trait AccessControlAllowHeaders

  /**
   * The Access-Control-Allow-Headers response header is used in response to a
   * preflight request which includes the Access-Control-Request-Headers to
   * indicate which HTTP headers can be used during the actual request.
   */
  object AccessControlAllowHeaders {

    final case class AccessControlAllowHeadersValue(values: Chunk[CharSequence]) extends AccessControlAllowHeaders

    case object All extends AccessControlAllowHeaders

    case object NoHeaders extends AccessControlAllowHeaders
  }

  sealed trait AccessControlAllowMethods

  object AccessControlAllowMethods {

    final case class AllowMethods(methods: Chunk[Method]) extends AccessControlAllowMethods

    case object AllowAllMethods extends AccessControlAllowMethods

    case object NoMethodsAllowed extends AccessControlAllowMethods
  }

  /**
   * The Access-Control-Allow-Origin response header indicates whether the
   * response can be shared with requesting code from the given origin.
   *
   * For requests without credentials, the literal value "*" can be specified as
   * a wildcard; the value tells browsers to allow requesting code from any
   * origin to access the resource. Attempting to use the wildcard with
   * credentials results in an error.
   *
   * <origin> Specifies an origin. Only a single origin can be specified. If the
   * server supports clients from multiple origins, it must return the origin
   * for the specific client making the request.
   *
   * null Specifies the origin "null".
   */
  sealed trait AccessControlAllowOrigin {
    val origin: String
  }

  object AccessControlAllowOrigin {

    final case class ValidAccessControlAllowOrigin(value: String) extends AccessControlAllowOrigin {
      override val origin = value
    }

    case object InvalidAccessControlAllowOrigin extends AccessControlAllowOrigin {
      override val origin = ""
    }
  }

  /**
   * The Access-Control-Expose-Headers response header allows a server to
   * indicate which response headers should be made available to scripts running
   * in the browser, in response to a cross-origin request.
   */
  sealed trait AccessControlExposeHeaders

  object AccessControlExposeHeaders {

    final case class AccessControlExposeHeadersValue(values: Chunk[CharSequence]) extends AccessControlExposeHeaders

    case object All extends AccessControlExposeHeaders

    case object NoHeaders extends AccessControlExposeHeaders
  }

  /**
   * The Access-Control-Max-Age response header indicates how long the results
   * of a preflight request (that is the information contained in the
   * Access-Control-Allow-Methods and Access-Control-Allow-Headers headers) can
   * be cached.
   *
   * Maximum number of seconds the results can be cached, as an unsigned
   * non-negative integer. Firefox caps this at 24 hours (86400 seconds).
   * Chromium (prior to v76) caps at 10 minutes (600 seconds). Chromium
   * (starting in v76) caps at 2 hours (7200 seconds). The default value is 5
   * seconds.
   */
  sealed trait AccessControlMaxAge {
    val duration: Duration
  }

  object AccessControlMaxAge {

    /**
     * Valid AccessControlMaxAge with an unsigned non-negative negative for
     * seconds
     */
    final case class ValidAccessControlMaxAge(duration: Duration = 5 seconds) extends AccessControlMaxAge

    case object InvalidAccessControlMaxAge extends AccessControlMaxAge {
      override val duration: Duration = 5 seconds
    }

  }

  sealed trait AccessControlRequestHeaders

  /**
   * The Access-Control-Request-Headers request header is used by browsers when
   * issuing a preflight request to let the server know which HTTP headers the
   * client might send when the actual request is made (such as with
   * setRequestHeader()). The complementary server-side header of
   * Access-Control-Allow-Headers will answer this browser-side header.
   */
  object AccessControlRequestHeaders {
    final case class AccessControlRequestHeadersValue(values: Chunk[String]) extends AccessControlRequestHeaders

    case object AllRequestHeaders extends AccessControlRequestHeaders

    case object NoRequestHeaders extends AccessControlRequestHeaders
  }

  sealed trait AccessControlRequestMethod

  object AccessControlRequestMethod {
    final case class RequestMethod(method: Method) extends AccessControlRequestMethod

    case object InvalidMethod extends AccessControlRequestMethod
  }

  /**
   * Age header value.
   */
  sealed trait Age

  object Age {

    /**
     * The Age header contains the time in seconds the object was in a proxy
     * cache.
     */
    final case class AgeValue(seconds: Long) extends Age

    /**
     * The Age header value is invalid.
     */
    case object InvalidAgeValue extends Age
  }

  /**
   * The Allow header must be sent if the server responds with a 405 Method Not
   * Allowed status code to indicate which request methods can be used .
   */

  sealed trait Allow {
    val raw: String
  }

  object Allow {

    case object InvalidAllowMethod extends Allow {
      override val raw: String = ""
    }

    final case class AllowMethods(methods: Chunk[Method]) extends Allow {
      override val raw: String = methods.map(_.toString()).mkString(", ")
    }
  }

  sealed trait AuthenticationScheme {
    val name: String
  }

  object AuthenticationScheme {

    case object Basic extends AuthenticationScheme {
      override val name: String = "Basic"
    }

    case object Bearer extends AuthenticationScheme {
      override val name: String = "Bearer"
    }

    case object Digest extends AuthenticationScheme {
      override val name: String = "Digest"
    }

    case object HOBA extends AuthenticationScheme {
      override val name: String = "HOBA"
    }

    case object Mutual extends AuthenticationScheme {
      override val name: String = "Mutual"
    }

    case object Negotiate extends AuthenticationScheme {
      override val name: String = "Negotiate"
    }

    case object OAuth extends AuthenticationScheme {
      override val name: String = "OAuth"
    }

    case object Scram extends AuthenticationScheme {
      override val name: String = "SCRAM"
    }

    case object ScramSha1 extends AuthenticationScheme {
      override val name: String = "SCRAM-SHA-1"
    }

    case object ScramSha256 extends AuthenticationScheme {
      override val name: String = "SCRAM-SHA-256"
    }

    case object Vapid extends AuthenticationScheme {
      override val name: String = "vapid"
    }

    case object `AWS4-HMAC-SHA256` extends AuthenticationScheme {
      override val name: String = "AWS4-HMAC-SHA256"
    }

    case object Invalid extends AuthenticationScheme {
      override val name: String = ""
    }
  }

  /** Authorization header value. */
  sealed trait Authorization

  object Authorization {

    sealed trait AuthScheme

    object AuthScheme {
      final case class Basic(username: String, password: String) extends AuthScheme

      final case class Digest(
        response: String,
        username: String,
        realm: String,
        uri: URI,
        opaque: String,
        algorithm: String,
        qop: String,
        cnonce: String,
        nonce: String,
        nc: Int,
        userhash: Boolean,
      ) extends AuthScheme

      final case class Bearer(token: String) extends AuthScheme

      final case class Unparsed(authScheme: String, authParameters: String) extends AuthScheme
    }

    /**
     * The Authorization header value contains one of the auth schemes
     * [[AuthScheme]].
     */
    final case class AuthorizationValue(authScheme: AuthScheme) extends Authorization

    /** The Authorization header value is invalid. */
    case object InvalidAuthorizationValue extends Authorization
  }

  /**
   * CacheControl header value.
   */
  sealed trait CacheControl {
    val raw: String
  }

  object CacheControl {

    /**
     * The immutable response directive indicates that the response will not be
     * updated while it's fresh
     */
    case object Immutable extends CacheControl {
      override val raw: String = "immutable"
    }

    /**
     * Signals an invalid value present in the header value.
     */
    case object InvalidCacheControl extends CacheControl {
      override val raw: String = "Invalid header value"
    }

    /**
     * The max-age=N response directive indicates that the response remains
     * fresh until N seconds after the response is generated.
     *
     * The max-age=N request directive indicates that the client allows a stored
     * response that is generated on the origin server within N seconds
     */
    final case class MaxAge(freshForSeconds: Int) extends CacheControl {
      override val raw: String = "max-age"
    }

    /**
     * The max-stale=N request directive indicates that the client allows a
     * stored response that is stale within N seconds.
     */
    final case class MaxStale(staleWithinSeconds: Int) extends CacheControl {
      override val raw: String = "max-stale"
    }

    /**
     * The min-fresh=N request directive indicates that the client allows a
     * stored response that is fresh for at least N seconds.
     */
    final case class MinFresh(freshAtLeastSeconds: Int) extends CacheControl {
      override val raw: String = "min-fresh"
    }

    /**
     * The must-revalidate response directive indicates that the response can be
     * stored in caches and can be reused while fresh. If the response becomes
     * stale, it must be validated with the origin server before reuse.
     */
    case object MustRevalidate extends CacheControl {
      override val raw: String = "must-revalidate"
    }

    /**
     * The must-understand response directive indicates that a cache should
     * store the response only if it understands the requirements for caching
     * based on status code.
     */
    case object MustUnderstand extends CacheControl {
      override val raw: String = "must-understand"
    }

    /**
     * Maintains a chunk of CacheControl values.
     */
    final case class MultipleCacheControlValues(values: Chunk[CacheControl]) extends CacheControl {
      override val raw: String = values.map(_.raw).mkString(",")
    }

    /**
     * The no-cache response directive indicates that the response can be stored
     * in caches, but the response must be validated with the origin server
     * before each reuse.
     *
     * The no-cache request directive asks caches to validate the response with
     * the origin server before reuse.
     */
    case object NoCache extends CacheControl {
      override val raw: String = "no-cache"
    }

    /**
     * The no-store response directive indicates that any caches of any kind
     * (private or shared) should not store this response.
     *
     * The no-store request directive allows a client to request that caches
     * refrain from storing the request and corresponding response — even if the
     * origin server's response could be stored.
     */
    case object NoStore extends CacheControl {
      override val raw: String = "no-store"
    }

    /**
     * The no-transform indicates that any intermediary (regardless of whether
     * it implements a cache) shouldn't transform the response/request contents.
     */
    case object NoTransform extends CacheControl {
      override val raw: String = "no-transform"
    }

    /**
     * The client indicates that cache should obtain an already-cached response.
     * If a cache has stored a response, it's reused.
     */
    case object OnlyIfCached extends CacheControl {
      override val raw: String = "only-if-cached"
    }

    /**
     * The private response directive indicates that the response can be stored
     * only in a private cache
     */
    case object Private extends CacheControl {
      override val raw: String = "private"
    }

    /**
     * The proxy-revalidate response directive is the equivalent of
     * must-revalidate, but specifically for shared caches only.
     */
    case object ProxyRevalidate extends CacheControl {
      override val raw: String = "proxy-revalidate"
    }

    /**
     * The public response directive indicates that the response can be stored
     * in a shared cache.
     */
    case object Public extends CacheControl {
      override val raw: String = "public"
    }

    /**
     * The s-maxage response directive also indicates how long the response is
     * fresh for (similar to max-age) — but it is specific to shared caches, and
     * they will ignore max-age when it is present.
     */
    final case class SMaxAge(freshForSeconds: Int) extends CacheControl {
      override val raw: String = "s-maxage"
    }

    /**
     * The stale-if-error response directive indicates that the cache can reuse
     * a stale response when an origin server responds with an error (500, 502,
     * 503, or 504).
     */
    final case class StaleIfError(seconds: Int) extends CacheControl {
      override val raw: String = "stale-if-error"
    }

    /**
     * The stale-while-revalidate response directive indicates that the cache
     * could reuse a stale response while it revalidates it to a cache.
     */
    final case class StaleWhileRevalidate(seconds: Int) extends CacheControl {
      override val raw: String = "stale-while-revalidate"
    }
  }

  /**
   * Connection header value.
   */
  sealed trait Connection {
    val value: String
  }

  object Connection {

    /**
     * This directive indicates that either the client or the server would like
     * to close the connection. This is the default on HTTP/1.0 requests.
     */
    case object Close extends Connection {
      override val value: String = "close"
    }

    /**
     * Any comma-separated list of HTTP headers [Usually keep-alive only]
     * indicates that the client would like to keep the connection open. Keeping
     * a connection open is the default on HTTP/1.1 requests. The list of
     * headers are the name of the header to be removed by the first
     * non-transparent proxy or cache in-between: these headers define the
     * connection between the emitter and the first entity, not the destination
     * node.
     */
    case object KeepAlive extends Connection {
      override val value: String = "keep-alive"
    }

    /**
     * Any string other than "close" and "keep-alive" will be treated as
     * InvalidConnection.
     */
    case object InvalidConnection extends Connection {
      override val value: String = ""
    }
  }

  sealed trait ContentBase

  object ContentBase {
    final case class BaseUri(uri: URI) extends ContentBase

    case object InvalidContentBase extends ContentBase
  }

  sealed trait ContentDisposition

  object ContentDisposition {
    final case class Attachment(filename: Option[String]) extends ContentDisposition

    final case class Inline(filename: Option[String]) extends ContentDisposition

    final case class FormData(name: String, filename: Option[String]) extends ContentDisposition

    case object InvalidContentDisposition extends ContentDisposition
  }

  sealed trait ContentEncoding {
    val encoding: String
  }

  object ContentEncoding {

    /**
     * InvalidEncoding is represented with ""
     */
    case object InvalidContentEncoding extends ContentEncoding {
      override val encoding: String = ""
    }

    /**
     * A format using the Brotli algorithm.
     */
    case object BrEncoding extends ContentEncoding {
      override val encoding: String = "br"
    }

    /**
     * A format using the Lempel-Ziv-Welch (LZW) algorithm. The value name was
     * taken from the UNIX compress program, which implemented this algorithm.
     * Like the compress program, which has disappeared from most UNIX
     * distributions, this content-encoding is not used by many browsers today,
     * partly because of a patent issue (it expired in 2003).
     */
    case object CompressEncoding extends ContentEncoding {
      override val encoding: String = "compress"
    }

    /**
     * Using the zlib structure (defined in RFC 1950) with the deflate
     * compression algorithm (defined in RFC 1951).
     */
    case object DeflateEncoding extends ContentEncoding {
      override val encoding: String = "deflate"
    }

    /**
     * A format using the Lempel-Ziv coding (LZ77), with a 32-bit CRC. This is
     * the original format of the UNIX gzip program. The HTTP/1.1 standard also
     * recommends that the servers supporting this content-encoding should
     * recognize x-gzip as an alias, for compatibility purposes.
     */
    case object GZipEncoding extends ContentEncoding {
      override val encoding: String = "gzip"
    }

    /**
     * Maintains a list of ContentEncoding values.
     */
    final case class MultipleEncodings(encodings: Chunk[ContentEncoding]) extends ContentEncoding {
      override val encoding: String = encodings.map(_.encoding).mkString(",")
    }
  }

  sealed trait ContentLanguage

  object ContentLanguage {
    case object Arabic extends ContentLanguage

    case object Bulgarian extends ContentLanguage

    case object Catalan extends ContentLanguage

    case object Chinese extends ContentLanguage

    case object Croatian extends ContentLanguage

    case object Czech extends ContentLanguage

    case object Danish extends ContentLanguage

    case object Dutch extends ContentLanguage

    case object English extends ContentLanguage

    case object Estonian extends ContentLanguage

    case object Finnish extends ContentLanguage

    case object French extends ContentLanguage

    case object German extends ContentLanguage

    case object Greek extends ContentLanguage

    case object Hebrew extends ContentLanguage

    case object Hindi extends ContentLanguage

    case object Hungarian extends ContentLanguage

    case object Icelandic extends ContentLanguage

    case object Indonesian extends ContentLanguage

    case object Italian extends ContentLanguage

    case object Japanese extends ContentLanguage

    case object Korean extends ContentLanguage

    case object Latvian extends ContentLanguage

    case object Lithuanian extends ContentLanguage

    case object Norwegian extends ContentLanguage

    case object Polish extends ContentLanguage

    case object Portuguese extends ContentLanguage

    case object Romanian extends ContentLanguage

    case object Russian extends ContentLanguage

    case object Serbian extends ContentLanguage

    case object Slovak extends ContentLanguage

    case object Slovenian extends ContentLanguage

    case object Spanish extends ContentLanguage

    case object Swedish extends ContentLanguage

    case object Thai extends ContentLanguage

    case object Turkish extends ContentLanguage

    case object Ukrainian extends ContentLanguage

    case object Vietnamese extends ContentLanguage

    case object InvalidContentLanguage extends ContentLanguage
  }

  /**
   * ContentLength header value
   */
  sealed trait ContentLength

  object ContentLength {

    /**
     * The Content-Length header indicates the size of the message body, in
     * bytes, sent to the recipient.
     */
    final case class ContentLengthValue(length: Long) extends ContentLength

    /**
     * The ContentLength header value is invalid.
     */
    case object InvalidContentLengthValue extends ContentLength
  }

  sealed trait ContentLocation

  object ContentLocation {
    final case class ContentLocationValue(value: URI) extends ContentLocation

    case object InvalidContentLocationValue extends ContentLocation

  }

  sealed trait ContentMd5

  object ContentMd5 {
    final case class ContentMd5Value(value: String) extends ContentMd5

    object InvalidContentMd5Value extends ContentMd5

  }

  sealed trait ContentRange {
    def start: Option[Int]

    def end: Option[Int]

    def total: Option[Int]

    def unit: String
  }

  object ContentRange {
    final case class ContentRangeStartEndTotal(unit: String, s: Int, e: Int, t: Int) extends ContentRange {
      def start: Option[Int] = Some(s)

      def end: Option[Int] = Some(e)

      def total: Option[Int] = Some(t)
    }

    final case class ContentRangeStartEnd(unit: String, s: Int, e: Int) extends ContentRange {
      def start: Option[Int] = Some(s)

      def end: Option[Int] = Some(e)

      def total: Option[Int] = None
    }

    final case class ContentRangeTotal(unit: String, t: Int) extends ContentRange {
      def start: Option[Int] = None

      def end: Option[Int] = None

      def total: Option[Int] = Some(t)
    }

    case object InvalidContentRange extends ContentRange {
      def start: Option[Int] = None

      def end: Option[Int] = None

      def total: Option[Int] = None

      def unit: String = ""
    }
  }

  sealed trait ContentTransferEncoding

  object ContentTransferEncoding {
    case object SevenBit extends ContentTransferEncoding

    case object EightBit extends ContentTransferEncoding

    case object Binary extends ContentTransferEncoding

    case object QuotedPrintable extends ContentTransferEncoding

    case object Base64 extends ContentTransferEncoding

    final case class XToken(token: String) extends ContentTransferEncoding

    case object InvalidContentTransferEncoding extends ContentTransferEncoding
  }

  sealed trait ContentType extends Product with Serializable {
    self =>
    def toStringValue: String
  }

  object ContentType {
    final case class ContentTypeValue(value: MediaType) extends ContentType {
      override def toStringValue: String = value.toString
    }

    case object InvalidContentType extends ContentType {
      override def toStringValue: String = ""
    }

  }

  sealed trait Date

  /**
   * The Date general HTTP header contains the date and time at which the
   * message originated.
   */
  object Date {
    case object InvalidDate extends Date

    final case class ValidDate(value: ZonedDateTime) extends Date
  }

  sealed trait DNT

  object DNT {
    case object InvalidDNTValue extends DNT

    case object TrackingAllowedDNTValue extends DNT

    case object TrackingNotAllowedDNTValue extends DNT

    case object NotSpecifiedDNTValue extends DNT
  }

  sealed trait ETag

  object ETag {
    case object InvalidETagValue extends ETag

    case class StrongETagValue(validator: String) extends ETag

    case class WeakETagValue(validator: String) extends ETag
  }

  /**
   * The Expect HTTP request header indicates expectations that need to be met
   * by the server to handle the request successfully. There is only one defined
   * expectation: 100-continue
   */
  sealed trait Expect {
    val value: String
  }

  object Expect {
    case object ExpectValue extends Expect {
      override val value: String = "100-continue"
    }

    case object InvalidExpectValue extends Expect {
      override val value: String = ""
    }
  }

  sealed trait Expires {
    def value: ZonedDateTime
  }

  /**
   * The Expires HTTP header contains the date/time after which the response is
   * considered expired.
   *
   * Invalid expiration dates with value 0 represent a date in the past and mean
   * that the resource is already expired.
   *
   * Expires: <Date>
   *
   * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
   *
   * Example:
   *
   * Expires: Wed, 21 Oct 2015 07:28:00 GMT
   */
  object Expires {
    final case class ValidExpires(date: ZonedDateTime) extends Expires {
      override def value: ZonedDateTime = date
    }

    case object InvalidExpires extends Expires {
      override def value: ZonedDateTime = ZonedDateTime.of(0, 0, 0, 0, 0, 0, 0, ZoneId.systemDefault())

    }
  }

  sealed trait From

  object From {

    /**
     * The From Header value is invalid
     */
    case object InvalidFromValue extends From

    final case class FromValue(email: String) extends From

  }

  sealed trait Host

  object Host {
    final case class HostValue(hostAddress: String, port: Option[Int] = None) extends Host

    object HostValue {
      def apply(hostAddress: String, port: Int): HostValue = HostValue(hostAddress, Some(port))
    }

    case object EmptyHostValue extends Host

    case object InvalidHostValue extends Host
  }

  sealed trait IfMatch

  object IfMatch {
    case object Any extends IfMatch

    case object None extends IfMatch

    final case class ETags(etags: Chunk[String]) extends IfMatch
  }

  sealed trait IfModifiedSince

  object IfModifiedSince {
    final case class ModifiedSince(value: ZonedDateTime) extends IfModifiedSince

    case object InvalidModifiedSince extends IfModifiedSince

  }

  sealed trait IfNoneMatch

  object IfNoneMatch {
    case object Any extends IfNoneMatch

    case object None extends IfNoneMatch

    final case class ETags(etags: Chunk[String]) extends IfNoneMatch
  }

  sealed trait IfRange

  object IfRange {

    final case class ETagValue(value: String) extends IfRange

    final case class DateTimeValue(value: ZonedDateTime) extends IfRange

    case object InvalidIfRangeValue extends IfRange

  }

  sealed trait IfUnmodifiedSince

  /**
   * If-Unmodified-Since request header makes the request for the resource
   * conditional: the server will send the requested resource or accept it in
   * the case of a POST or another non-safe method only if the resource has not
   * been modified after the date specified by this HTTP header.
   */
  object IfUnmodifiedSince {
    final case class UnmodifiedSince(value: ZonedDateTime) extends IfUnmodifiedSince

    case object InvalidUnmodifiedSince extends IfUnmodifiedSince
  }

  sealed trait LastModified

  object LastModified {
    final case class LastModifiedDateTime(dateTime: ZonedDateTime) extends LastModified

    case object InvalidLastModified extends LastModified
  }

  /**
   * Location header value.
   */
  sealed trait Location

  object Location {

    /**
     * The Location header contains URL of the new Resource
     */
    final case class LocationValue(url: URL) extends Location

    /**
     * The URL header value is invalid.
     */
    case object EmptyLocationValue extends Location

  }

  /**
   * Max-Forwards header value
   */
  sealed trait MaxForwards

  object MaxForwards {

    final case class MaxForwardsValue(value: Int) extends MaxForwards

    case object InvalidMaxForwardsValue extends MaxForwards

  }

  /** Origin header value. */
  sealed trait Origin

  object Origin {

    /** The Origin header value is privacy sensitive or is an opaque origin. */
    case object OriginNull extends Origin

    /** The Origin header value contains scheme, host and maybe port. */
    final case class OriginValue(scheme: String, host: String, port: Option[Int]) extends Origin

    /** The Origin header value is invalid. */
    case object InvalidOriginValue extends Origin
  }

  /** Pragma header value. */
  sealed trait Pragma

  object Pragma {

    /** Pragma no-cache value. */
    case object PragmaNoCacheValue extends Pragma

    /** Invalid pragma value. */
    case object InvalidPragmaValue extends Pragma

  }

  /**
   * The HTTP Proxy-Authenticate response header defines the authentication
   * method that should be used to gain access to a resource behind a proxy
   * server. It authenticates the request to the proxy server, allowing it to
   * transmit the request further.
   */
  sealed trait ProxyAuthenticate

  object ProxyAuthenticate {

    /**
     * @param scheme
     *   Authentication type
     * @param realm
     *   A description of the protected area, the realm. If no realm is
     *   specified, clients often display a formatted host name instead.
     */
    final case class ValidProxyAuthenticate(scheme: AuthenticationScheme, realm: Option[String])
        extends ProxyAuthenticate

    case object InvalidProxyAuthenticate extends ProxyAuthenticate

  }

  sealed trait ProxyAuthorization {
    val value: String
  }

  /**
   * The HTTP Proxy-Authorization request header contains the credentials to
   * authenticate a user agent to a proxy server, usually after the server has
   * responded with a 407 Proxy Authentication Required status and the
   * Proxy-Authenticate header.
   */
  object ProxyAuthorization {

    /**
     * Proxy-Authorization: <type> <credentials>
     *
     * <type> - AuthenticationScheme
     *
     * <credentials> - The resulting string is base64 encoded
     *
     * Example
     *
     * Proxy-Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l
     */
    final case class ValidProxyAuthorization(authenticationScheme: AuthenticationScheme, credential: String)
        extends ProxyAuthorization {
      override val value = s"${authenticationScheme.name} ${credential}"
    }

    case object InvalidProxyAuthorization extends ProxyAuthorization {
      override val value: String = ""
    }

  }

  sealed trait Range

  object Range {

    final case class SingleRange(unit: String, start: Long, end: Option[Long]) extends Range

    final case class MultipleRange(unit: String, ranges: List[(Long, Option[Long])]) extends Range

    final case class SuffixRange(unit: String, value: Long) extends Range

    final case class PrefixRange(unit: String, value: Long) extends Range

    case object InvalidRange extends Range

  }

  /**
   * The Referer HTTP request header contains the absolute or partial address
   * from which a resource has been requested. The Referer header allows a
   * server to identify referring pages that people are visiting from or where
   * requested resources are being used. This data can be used for analytics,
   * logging, optimized caching, and more.
   *
   * When you click a link, the Referer contains the address of the page that
   * includes the link. When you make resource requests to another domain, the
   * Referer contains the address of the page that uses the requested resource.
   *
   * The Referer header can contain an origin, path, and querystring, and may
   * not contain URL fragments (i.e. #section) or username:password information.
   * The request's referrer policy defines the data that can be included. See
   * Referrer-Policy for more information and examples.
   */
  sealed trait Referer

  object Referer {

    /**
     * The Location header contains URL of the new Resource
     */
    final case class ValidReferer(url: URL) extends Referer

    /**
     * The URL header value is invalid.
     */
    case object InvalidReferer extends Referer
  }

  sealed trait RequestCookie

  /**
   * The Cookie HTTP request header contains stored HTTP cookies associated with
   * the server.
   */
  object RequestCookie {

    final case class CookieValue(value: List[Cookie[Request]]) extends RequestCookie

    final case class InvalidCookieValue(error: Exception) extends RequestCookie

  }

  sealed trait ResponseCookie

  object ResponseCookie {
    final case class CookieValue(value: Cookie[Response]) extends ResponseCookie

    final case class InvalidCookieValue(error: Exception) extends ResponseCookie
  }

  sealed trait RetryAfter

  /**
   * The RetryAfter HTTP header contains the date/time after which to retry
   *
   * Invalid RetryAfter with value 0
   *
   * RetryAfter: <Date>
   *
   * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
   *
   * Example:
   *
   * Expires: Wed, 21 Oct 2015 07:28:00 GMT
   *
   * Or RetryAfter the delay seconds.
   */
  object RetryAfter {

    final case class RetryAfterByDate(date: ZonedDateTime) extends RetryAfter

    final case class RetryAfterByDuration(delay: Duration) extends RetryAfter

    case object InvalidRetryAfter extends RetryAfter
  }

  sealed trait SecWebSocketAccept

  /**
   * The Sec-WebSocket-Accept header is used in the websocket opening handshake.
   * It would appear in the response headers. That is, this is header is sent
   * from server to client to inform that server is willing to initiate a
   * websocket connection.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Accept
   */
  object SecWebSocketAccept {
    final case class HashedKey(value: String) extends SecWebSocketAccept

    case object InvalidHashedKey extends SecWebSocketAccept

  }

  sealed trait SecWebSocketExtensions

  /**
   * The Sec-WebSocket-Extensions header is used in the WebSocket handshake. It
   * is initially sent from the client to the server, and then subsequently sent
   * from the server to the client, to agree on a set of protocol-level
   * extensions to use during the connection.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Extensions
   */
  object SecWebSocketExtensions {
    // Sec-WebSocket-Extensions: foo, bar; baz=2

    // Sec-WebSocket-Extensions: deflate-stream
    //         Sec-WebSocket-Extensions: mux; max-channels=4; flow-control,
    //          deflate-stream
    //         Sec-WebSocket-Extensions: private-extension

    // Sec-WebSocket-Extensions = extension-list
    //         extension-list = 1#extension
    //         extension = extension-token *( ";" extension-param )
    //         extension-token = registered-token
    //         registered-token = token
    //         extension-param = token [ "=" (token | quoted-string) ]
    //             ;When using the quoted-string syntax variant, the value
    //             ;after quoted-string unescaping MUST conform to the
    //             ;'token' ABNF.

    sealed trait Extension

    object Extension {
      final case class Parameter(name: String, value: String) extends Extension

      final case class TokenParam(name: String) extends Extension
    }

    final case class Token(extension: Chunk[Extension]) extends SecWebSocketExtensions

    final case class Extensions(extensions: Chunk[Token]) extends SecWebSocketExtensions

    case object InvalidExtensions extends SecWebSocketExtensions

  }

  sealed trait SecWebSocketKey

  /**
   * The Sec-WebSocket-Key header is used in the WebSocket handshake. It is sent
   * from the client to the server to provide part of the information used by
   * the server to prove that it received a valid WebSocket handshake. This
   * helps ensure that the server does not accept connections from non-WebSocket
   * clients (e.g. HTTP clients) that are being abused to send data to
   * unsuspecting WebSocket servers.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Key
   */
  object SecWebSocketKey {
    case class Base64EncodedKey(key: String) extends SecWebSocketKey

    case object InvalidKey extends SecWebSocketKey

  }

  sealed trait SecWebSocketLocation

  object SecWebSocketLocation {
    final case class LocationValue(url: URL) extends SecWebSocketLocation

    case object EmptyLocationValue extends SecWebSocketLocation

  }

  sealed trait SecWebSocketOrigin

  /**
   * The Sec-WebSocket-Origin header is used to protect against unauthorized
   * cross-origin use of a WebSocket server by scripts using the |WebSocket| API
   * in a Web browser. The server is informed of the script origin generating
   * the WebSocket connection request.
   */
  object SecWebSocketOrigin {
    final case class OriginValue(url: URL) extends SecWebSocketOrigin

    case object EmptyOrigin extends SecWebSocketOrigin

  }

  sealed trait SecWebSocketProtocol

  /**
   * The Sec-WebSocket-Protocol header field is used in the WebSocket opening
   * handshake. It is sent from the client to the server and back from the
   * server to the client to confirm the subprotocol of the connection. This
   * enables scripts to both select a subprotocol and be sure that the server
   * agreed to serve that subprotocol.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Protocol
   */
  object SecWebSocketProtocol {
    // https://www.iana.org/assignments/websocket/websocket.xml#subprotocol-name

    final case class Protocols(subProtocols: Chunk[String]) extends SecWebSocketProtocol

    case object InvalidProtocol extends SecWebSocketProtocol

  }

  sealed trait SecWebSocketVersion

  /**
   * The Sec-WebSocket-Version header field is used in the WebSocket opening
   * handshake. It is sent from the client to the server to indicate the
   * protocol version of the connection.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Version
   */
  object SecWebSocketVersion {
    // https://www.iana.org/assignments/websocket/websocket.xml#version-number

    final case class Version(version: Int) extends SecWebSocketVersion

    case object InvalidVersion extends SecWebSocketVersion

  }

  /**
   * Server header value.
   */
  sealed trait Server

  object Server {

    /**
     * A server value with a name
     */
    final case class ServerName(name: String) extends Server

    /**
     * No server name
     */
    object EmptyServerName extends Server
  }

  sealed trait Te {
    def raw: String
  }

  object Te {

    /**
     * Signals an invalid value present in the header value.
     */
    case object InvalidEncoding extends Te {
      override def raw: String = "Invalid encoding"
    }

    /**
     * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
     */
    final case class CompressEncoding(weight: Option[Double]) extends Te {
      override def raw: String = "compress"
    }

    /**
     * A compression format that uses the zlib structure with the deflate
     * compression algorithm.
     */
    final case class DeflateEncoding(weight: Option[Double]) extends Te {
      override def raw: String = "deflate"
    }

    /**
     * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
     * CRC.
     */
    final case class GZipEncoding(weight: Option[Double]) extends Te {
      override def raw: String = "gzip"
    }

    /**
     * Indicates the identity function (that is, without modification or
     * compression). This value is always considered as acceptable, even if
     * omitted.
     */
    case object Trailers extends Te {
      override def raw: String = "trailers"
    }

    /**
     * Maintains a chunk of AcceptEncoding values.
     */
    final case class MultipleEncodings(encodings: Chunk[Te]) extends Te {
      override def raw: String = encodings.mkString(",")
    }
  }

  /** Trailer header value. */
  sealed trait Trailer

  object Trailer {

    case class TrailerValue(header: String) extends Trailer

    /** Invalid Trailer value. */
    case object InvalidTrailerValue extends Trailer

  }

  sealed trait TransferEncoding {
    val encoding: String
  }

  object TransferEncoding {

    /**
     * InvalidEncoding is represented with ""
     */
    case object InvalidTransferEncoding extends TransferEncoding {
      override val encoding: String = ""
    }

    /**
     * Data is sent in a series of chunks.
     */
    case object ChunkedEncoding extends TransferEncoding {
      override val encoding: String = "chunked"
    }

    /**
     * A format using the Lempel-Ziv-Welch (LZW) algorithm. The value name was
     * taken from the UNIX compress program, which implemented this algorithm.
     * Like the compress program, which has disappeared from most UNIX
     * distributions, this content-encoding is not used by many browsers today,
     * partly because of a patent issue (it expired in 2003).
     */
    case object CompressEncoding extends TransferEncoding {
      override val encoding: String = "compress"
    }

    /**
     * Using the zlib structure (defined in RFC 1950) with the deflate
     * compression algorithm (defined in RFC 1951).
     */
    case object DeflateEncoding extends TransferEncoding {
      override val encoding: String = "deflate"
    }

    /**
     * A format using the Lempel-Ziv coding (LZ77), with a 32-bit CRC. This is
     * the original format of the UNIX gzip program. The HTTP/1.1 standard also
     * recommends that the servers supporting this content-encoding should
     * recognize x-gzip as an alias, for compatibility purposes.
     */
    case object GZipEncoding extends TransferEncoding {
      override val encoding: String = "gzip"
    }

    /**
     * Maintains a list of TransferEncoding values.
     */
    final case class MultipleEncodings(encodings: Chunk[TransferEncoding]) extends TransferEncoding {
      override val encoding: String = encodings.map(_.encoding).mkString(",")
    }

  }

  sealed trait Upgrade

  object Upgrade {
    final case class UpgradeProtocols(protocols: Chunk[UpgradeValue]) extends Upgrade

    final case class UpgradeValue(protocol: String, version: String) extends Upgrade

    case object InvalidUpgradeValue extends Upgrade

  }
  sealed trait UpgradeInsecureRequests

  /**
   * The HTTP Upgrade-Insecure-Requests request header sends a signal to the
   * server expressing the client's preference for an encrypted and
   * authenticated response.
   */
  object UpgradeInsecureRequests {
    case object UpgradeInsecureRequests extends UpgradeInsecureRequests

    case object InvalidUpgradeInsecureRequests extends UpgradeInsecureRequests

  }

  sealed trait UserAgent

  /**
   * The "User-Agent" header field contains information about the user agent
   * originating the request, which is often used by servers to help identify
   * the scope of reported interoperability problems, to work around or tailor
   * responses to avoid particular user agent limitations, and for analytics
   * regarding browser or operating system use
   */
  object UserAgent {
    final case class CompleteUserAgent(product: Product, comment: Option[Comment]) extends UserAgent

    final case class Product(name: String, version: Option[String]) extends UserAgent

    final case class Comment(comment: String) extends UserAgent

    object InvalidUserAgent extends UserAgent
  }

  /** Vary header value. */
  sealed trait Vary

  object Vary {
    case class HeadersVaryValue(headers: List[String]) extends Vary

    case object StarVary extends Vary

    case object InvalidVaryValue extends Vary
  }

  sealed trait Via

  /**
   * The Via general header is added by proxies, both forward and reverse, and
   * can appear in the request or response headers. It is used for tracking
   * message forwards, avoiding request loops, and identifying the protocol
   * capabilities of senders along the request/response chain
   */
  object Via {
    sealed trait ReceivedProtocol

    object ReceivedProtocol {
      final case class Version(version: String) extends ReceivedProtocol

      final case class ProtocolVersion(protocol: String, version: String) extends ReceivedProtocol

      case object InvalidProtocol extends ReceivedProtocol
    }

    final case class ViaValues(values: Chunk[Via]) extends Via

    final case class DetailedValue(receivedProtocol: ReceivedProtocol, receivedBy: String, comment: Option[String])
        extends Via

    case object InvalidVia extends Via
  }

  sealed trait Warning {}

  /*
  The Warning HTTP header contains information about possible problems with the status of the message.
    More than one Warning header may appear in a response.

  Warning header fields can, in general, be applied to any message.
    However, some warn-codes are specific to caches and can only be applied to response messages.
   */

  object Warning {

    /*
       A warning has the following syntax: <warn-code> <warn-agent> <warn-text> [<warn-date>]
     */
    final case class WarningValue(code: Int, agent: String, text: String, date: Option[ZonedDateTime] = None)
        extends Warning

    case object InvalidWarning extends Warning
  }

  sealed trait WWWAuthenticate

  object WWWAuthenticate {
    final case class Basic(realm: String, charset: String = "UTF-8") extends WWWAuthenticate

    final case class Bearer(
      realm: String,
      scope: Option[String] = None,
      error: Option[String] = None,
      errorDescription: Option[String] = None,
    ) extends WWWAuthenticate

    final case class Digest(
      realm: Option[String],
      domain: Option[String] = None,
      nonce: Option[String] = None,
      opaque: Option[String] = None,
      stale: Option[Boolean] = None,
      algorithm: Option[String] = None,
      qop: Option[String] = None,
      charset: Option[String] = None,
      userhash: Option[Boolean] = None,
    ) extends WWWAuthenticate

    final case class HOBA(realm: Option[String], challenge: String, maxAge: Int) extends WWWAuthenticate

    final case class Mutual(realm: String, error: Option[String] = None, errorDescription: Option[String] = None)
        extends WWWAuthenticate

    final case class Negotiate(authData: Option[String] = None) extends WWWAuthenticate

    final case class SCRAM(
      realm: String,
      sid: String,
      data: String,
    ) extends WWWAuthenticate

    final case class `AWS4-HMAC-SHA256`(
      realm: String,
      credentials: Option[String] = None,
      signedHeaders: String,
      signature: String,
    ) extends WWWAuthenticate

    final case class Unknown(scheme: String, realm: String, params: Map[String, String]) extends WWWAuthenticate
  }

  sealed trait XFrameOptions

  object XFrameOptions {

    case object Deny extends XFrameOptions

    case object SameOrigin extends XFrameOptions

    case object Invalid extends XFrameOptions

  }

  sealed trait XRequestedWith

  object XRequestedWith {
    final case class XMLHttpRequest(value: String) extends XRequestedWith

  }

}
