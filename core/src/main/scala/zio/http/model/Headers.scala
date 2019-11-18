package zio.http.model
import java.net.URL
import java.net.URI
import java.time.LocalDateTime
import zio.duration.Duration

sealed trait RequestHeader

/*
    Accept header field used to specify which response media types are acceptable

    E.g. Accept: audio/\*; q=0.2, audio/basic
 */

final case class Accept(mediaRange: List[MediaRange]) extends RequestHeader

/*
    Accept-Charset header field used to indicate what charsets are
    acceptable in textual response content.

    E.g. Accept-Charset: iso-8859-5, unicode-1-1;q=0.8
 */

final case class AcceptCharset(charsets: List[CharsetRange]) extends RequestHeader

/*
    Accept-Encoding header field to indicate what response
    content-codings are acceptable in the response

    E.g. Accept-Encoding: gzip;q=1.0, identity; q=0.5, *;q=0
 */

final case class AcceptEncoding(contentCodings: List[ContentCoding]) extends RequestHeader

/*

    Accept-Language header field to indicate the set of natural
    languages that are preferred in the response.

    E.g. Accept-Language: da, en-gb;q=0.8, en;q=0.7

 */

final case class AcceptLanguage(languages: List[Language]) extends RequestHeader

final case class Authorization(scheme: AuthenticationScheme, credentials: Credentials) {
  override def toString = s"Authorization: $scheme $credentials"
}

/*
    Expect header field indicates the required server behaviour
    by the client.

    E.g 100-continue
 */
final case class Expect(value: String) extends RequestHeader

/*
    The From request-header field, if given, SHOULD contain an Internet
    e-mail address for the human user who controls the requesting user
    agent.
 */
final case class From(value: Email) extends RequestHeader

/*

    The Host request-header field specifies the Internet host and port
    number of the resource being requested.

 */
final case class Host(host: URL, port: Int) extends RequestHeader

final case class IfMatch(etags: List[ETag]) extends RequestHeader

/*
    If-Modified-Since request-header field is used to determine if the requested
    variant has not been modified since the time specified in this field. An entity
    will not be eturned from the server; instead, a 304 (not modified) response will
    be returned without any message-body.
 */
final case class IfModifiedSince(value: LocalDateTime) extends RequestHeader

/*
    If-None-Match request-header field is used with a method to make
    it conditional. A client that has one or more entities previously
    obtained from the resource can verify that none of those entities is
    current by including a list of their associated entity tags in the
    If-None-Match header field. The purpose of this feature is to allow
    efficient updates of cached information with a minimum amount of
    transaction overhead. It is also used to prevent a method (e.g. PUT)
    from inadvertently modifying an existing resource when the client
    believes that the resource does not exist.
 */
final case class IfNoneMatch(etags: List[ETag]) extends RequestHeader

final case class IfRange(value: Either[ETag, LocalDateTime]) extends RequestHeader

/*
   If-Unmodified-Since request-header field used to indicate the
   requested resource has not been modified since the time specified
   in this field, the server SHOULD perform the requested operation
   as if the If-Unmodified-Since header were not present.
 */
final case class IfUnmodifiedSince(value: LocalDateTime) extends RequestHeader

/*
    Max-Forwards request-header field provides a mechanism to limit the
    number of proxies or gateways that can forward the request to the
    next inbound server.
 */
final case class MaxForwards(value: Int) extends RequestHeader

final case class ProxyAuthorization(scheme: AuthenticationScheme, credentials: Credentials) extends RequestHeader {
  override def toString = s"ProxyAuthorization: $scheme $credentials"
}

final case class Range(value: String) extends RequestHeader

/*
    Referer[sic] request-header field allows the client to specify, for the server's benefit,
    the address (URI) of the resource from which the Request-URI was obtained
 */
final case class Referer(value: URI) extends RequestHeader

/*

    Transfer-coding is a request header property used to indicate
    an encoding transformation that has been, can be, or may need
    to be applied to an entity-body in order to ensure
    "safe transport" through the network. Not be conflated with
    content-type encoding. This encoding is particular to the message,
    being transported not the entity itself.

 */
import TransferEncoding._

sealed abstract class TransferEncoding(transferEncodings: List[TransferEncodingType]) extends RequestHeader {
  override def toString(): String = s"Tranfer-Encoding: ${transferEncodings.mkString(",")}"
}

object TransferEncoding {
  sealed trait TransferEncodingType
  final case object CHUNKED  extends TransferEncodingType
  final case object IDENTITY extends TransferEncodingType
  final case object GZIP     extends TransferEncodingType
  final case object COMPRESS extends TransferEncodingType
  final case object DEFLATE  extends TransferEncodingType
}

final case class UserAgent(product: String, productVersion: Option[String] = None, comment: Option[String] = None)
    extends RequestHeader

/*
    Response Headers
 */
sealed trait ResponseHeader

import AcceptRanges._

final case class AcceptRanges(value: AcceptRangesType) extends ResponseHeader

object AcceptRanges {
  sealed trait AcceptRangesType
  final case object NONE  extends AcceptRangesType
  final case object BYTES extends AcceptRangesType
}

final case class Age(value: Duration) extends ResponseHeader

/*
    ETag or Entity tags is used for comparing two or more entities from the same
    requested resource. Used by If-Match, If-None-Match, and If-Range header fields.
 */

final case class ETag(value: String) extends ResponseHeader

final case class Location(value: URL) extends ResponseHeader

final case class ProxyAuthenticate(scheme: AuthenticationScheme, realm: Realm) extends ResponseHeader {
  override def toString = s"ProxyAuthenticate: $scheme $realm"
}

final case class RetryAfter(duration: Either[LocalDateTime, Duration]) extends ResponseHeader

final case class Server(value: String) extends ResponseHeader

final case class Vary(value: String) extends ResponseHeader

final case class WwwAuthenticate(
  scheme: AuthenticationScheme,
  realm: Realm,
  parameters: Map[String, String],
  charset: Charset
) extends ResponseHeader {
  override def toString = s"WWW-Authenticate: $scheme $realm, charset=${charset.value}"
}
