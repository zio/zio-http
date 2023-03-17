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

package zio.http.model.headers.values

import zio.Chunk

sealed trait ContentEncoding {
  val encoding: String
}

object ContentEncoding {

  /**
   * A format using the Brotli algorithm.
   */
  case object Br extends ContentEncoding {
    override val encoding: String = "br"
  }

  /**
   * A format using the Lempel-Ziv-Welch (LZW) algorithm. The value name was
   * taken from the UNIX compress program, which implemented this algorithm.
   * Like the compress program, which has disappeared from most UNIX
   * distributions, this content-encoding is not used by many browsers today,
   * partly because of a patent issue (it expired in 2003).
   */
  case object Compress extends ContentEncoding {
    override val encoding: String = "compress"
  }

  /**
   * Using the zlib structure (defined in RFC 1950) with the deflate compression
   * algorithm (defined in RFC 1951).
   */
  case object Deflate extends ContentEncoding {
    override val encoding: String = "deflate"
  }

  /**
   * A format using the Lempel-Ziv coding (LZ77), with a 32-bit CRC. This is the
   * original format of the UNIX gzip program. The HTTP/1.1 standard also
   * recommends that the servers supporting this content-encoding should
   * recognize x-gzip as an alias, for compatibility purposes.
   */
  case object GZip extends ContentEncoding {
    override val encoding: String = "gzip"
  }

  /**
   * Maintains a list of ContentEncoding values.
   */
  final case class Multiple(encodings: Chunk[ContentEncoding]) extends ContentEncoding {
    override val encoding: String = encodings.map(_.encoding).mkString(",")
  }

  private def findEncoding(value: String): Option[ContentEncoding] = {
    value.trim match {
      case "br"       => Some(Br)
      case "compress" => Some(Compress)
      case "deflate"  => Some(Deflate)
      case "gzip"     => Some(GZip)
      case _          => None
    }
  }

  /**
   * @param value
   *   of string , seperated for multiple values
   * @return
   *   ContentEncoding
   *
   * Note: This implementation ignores the invalid string that might occur in
   * MultipleEncodings case.
   */
  def parse(value: CharSequence): Either[String, ContentEncoding] = {
    val encodings = Chunk.fromArray(value.toString.split(",").map(findEncoding)).flatten

    encodings match {
      case Chunk()       => Left("Empty ContentEncoding")
      case Chunk(single) => Right(single)
      case encodings     => Right(Multiple(encodings))
    }
  }

  def render(value: ContentEncoding): String = value.encoding

}
