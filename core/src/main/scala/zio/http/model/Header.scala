/*
 *
 *  Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package zio.http.model

import java.io.Serializable
import java.net.{ URI, URL }
import java.time.Instant

import zio.duration.Duration
import zio.http.model.ResponseHeader.ETag

sealed trait RequestHeader extends Product with Serializable

object RequestHeader {

  final case class Accept(mediaRange: List[MediaRange]) extends RequestHeader

  final case class AcceptCharset(charsets: List[CharsetRange]) extends RequestHeader

  final case class AcceptEncoding(contentCodings: List[ContentCoding]) extends RequestHeader

  final case class AcceptLanguage(languages: List[Language]) extends RequestHeader

  final case class Authorization(scheme: AuthenticationScheme, credentials: Credentials) extends RequestHeader

  final case class Expect(value: String) extends RequestHeader

  final case class From(value: Email) extends RequestHeader

  final case class Host(host: URL, port: Int) extends RequestHeader

  final case class IfMatch(etags: List[ETag]) extends RequestHeader

  final case class IfModifiedSince(value: Instant) extends RequestHeader

  final case class IfNoneMatch(etags: List[ETag]) extends RequestHeader

  final case class IfRange(value: Either[ETag, Instant]) extends RequestHeader

  final case class IfUnmodifiedSince(value: Instant) extends RequestHeader

  final case class MaxForwards(value: Int) extends RequestHeader

  final case class ProxyAuthorization(scheme: AuthenticationScheme, credentials: Credentials) extends RequestHeader

  final case class Range(value: String) extends RequestHeader

  final case class Referer(value: URI) extends RequestHeader

  final case class TransferEncoding(transferEncodings: List[TransferEncodingType]) extends RequestHeader

  sealed trait TransferEncodingType extends Product with Serializable

  object TransferEncodingType {
    case object CHUNKED  extends TransferEncodingType
    case object IDENTITY extends TransferEncodingType
    case object GZIP     extends TransferEncodingType
    case object COMPRESS extends TransferEncodingType
    case object DEFLATE  extends TransferEncodingType
  }

  final case class UserAgent(product: String, productVersion: Option[String] = None, comment: Option[String] = None)
      extends RequestHeader
}

sealed trait ResponseHeader extends Product with Serializable

object ResponseHeader {

  final case class AcceptRanges(value: AcceptRangesType) extends ResponseHeader

  sealed trait AcceptRangesType extends Product with Serializable

  object AcceptRangesType {
    case object NONE  extends AcceptRangesType
    case object BYTES extends AcceptRangesType
  }

  final case class Age(value: Duration) extends ResponseHeader

  final case class ETag(value: String) extends ResponseHeader

  final case class Location(value: URL) extends ResponseHeader

  final case class ProxyAuthenticate(scheme: AuthenticationScheme, realm: Realm) extends ResponseHeader

  final case class RetryAfter(duration: Either[Instant, Duration]) extends ResponseHeader

  final case class Server(value: String) extends ResponseHeader

  final case class Vary(value: String) extends ResponseHeader

  final case class WwwAuthenticate(
    scheme: AuthenticationScheme,
    realm: Realm,
    parameters: Map[String, String],
    charset: Charset
  ) extends ResponseHeader

  final case class SetCookie(
    value: String,
    domain: Option[String],
    path: Option[String],
    expires: Option[Instant],
    maxAge: Option[Long],
    secure: Boolean,
    httpOnly: Boolean
  ) extends ResponseHeader

}
