/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.h1

import zio.blocks.chunk.Chunk

final case class H1Header(name: String, value: String)

object H1Header {
  def fromStrings(name: String, value: String): Either[H1Error, H1Header] = {
    if (!H1Validation.isToken(name)) Left(H1Error.InvalidMessage("invalid header name: '" + name + "'"))
    else if (!H1Validation.isHeaderValue(value)) Left(H1Error.InvalidMessage("invalid header value: '" + value + "'"))
    else Right(H1Header(name, value))
  }
}

final case class H1Headers(fields: List[H1Header]) {
  def headerValues(name: String): List[String] =
    fields.collect { case H1Header(n, v) if n.equalsIgnoreCase(name) => v }

  def contains(name: String): Boolean = fields.exists(_.name.equalsIgnoreCase(name))
}

object H1Headers {
  val Empty: H1Headers = H1Headers(Nil)
}

sealed trait H1BodyFraming extends Product with Serializable

object H1BodyFraming {
  case object Empty                    extends H1BodyFraming
  final case class Fixed(length: Long) extends H1BodyFraming
  case object Chunked                  extends H1BodyFraming
}

final case class H1Request(
  method: String,
  target: String,
  headers: H1Headers,
  body: Chunk[Byte],
  framing: H1BodyFraming,
  trailers: H1Headers,
)

final case class H1Response(
  status: Int,
  reason: String,
  headers: H1Headers,
  body: Chunk[Byte],
  framing: H1BodyFraming,
  trailers: H1Headers,
)

sealed trait H1ConnectionPreference extends Product with Serializable

object H1ConnectionPreference {
  case object KeepAlive extends H1ConnectionPreference
  case object Close     extends H1ConnectionPreference
}

private[h1] object H1Validation {
  def isToken(s: String): Boolean = {
    var i = 0
    if (s.isEmpty) return false
    while (i < s.length) {
      if (!isTokenChar(s.charAt(i))) return false
      i += 1
    }
    true
  }

  def isTokenChar(c: Char): Boolean =
    (c >= 'a' && c <= 'z') ||
      (c >= 'A' && c <= 'Z') ||
      (c >= '0' && c <= '9') ||
      (c == '!') || (c == '#') || (c == '$') || (c == '%') || (c == '&') ||
      (c == '\'') || (c == '*') || (c == '+') || (c == '-') || (c == '.') ||
      (c == '^') || (c == '_') || (c == '`') || (c == '|') || (c == '~')

  def isHeaderValue(s: String): Boolean = {
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c < 0x20 || c == 0x7f) return false
      i += 1
    }
    true
  }

  def isReasonPhrase(s: String): Boolean = isHeaderValue(s)

  def isValidTarget(s: String): Boolean = {
    var i = 0
    if (s.isEmpty) return false
    while (i < s.length) {
      val c = s.charAt(i)
      if (c <= 0x20 || c == 0x7f) return false
      i += 1
    }
    true
  }

  def trimOWS(s: String): String = {
    var from = 0
    var to   = s.length
    while (from < to && (s.charAt(from) == ' ' || s.charAt(from) == '\t')) from += 1
    while (to > from && (s.charAt(to - 1) == ' ' || s.charAt(to - 1) == '\t')) to -= 1
    if (from == 0 && to == s.length) s else s.substring(from, to)
  }

  def equalsIgnoreCase(a: String, b: String): Boolean =
    a.length == b.length && a.equalsIgnoreCase(b)

  def parseContentLength(raw: String): Either[H1Error, Long] = {
    if (raw.isEmpty) return Left(H1Error.InvalidMessage("invalid Content-Length: '" + raw + "'"))
    var i      = 0
    var value  = 0L
    val length = raw.length
    while (i < length) {
      val c     = raw.charAt(i)
      if (c < '0' || c > '9') return Left(H1Error.InvalidMessage("invalid Content-Length: '" + raw + "'"))
      val digit = (c - '0').toLong
      if (value > (Long.MaxValue - digit) / 10L)
        return Left(H1Error.InvalidMessage("invalid Content-Length: '" + raw + "'"))
      value = value * 10L + digit
      i += 1
    }
    Right(value)
  }

  def parseChunkSize(raw: String): Either[H1Error, Long] = {
    if (raw.isEmpty) return Left(H1Error.InvalidMessage("invalid chunk size: '" + raw + "'"))
    var i     = 0
    var value = 0L
    while (i < raw.length) {
      val c     = raw.charAt(i)
      val digit =
        if (c >= '0' && c <= '9') (c - '0').toLong
        else if (c >= 'a' && c <= 'f') (c - 'a' + 10).toLong
        else if (c >= 'A' && c <= 'F') (c - 'A' + 10).toLong
        else return Left(H1Error.InvalidMessage("invalid chunk size: '" + raw + "'"))
      if ((value & 0xf000000000000000L) != 0L)
        return Left(H1Error.InvalidMessage("invalid chunk size: '" + raw + "'"))
      value = (value << 4) | digit
      i += 1
    }
    Right(value)
  }
}
