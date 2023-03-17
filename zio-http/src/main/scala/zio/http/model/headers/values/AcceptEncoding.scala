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

import scala.annotation.tailrec
import scala.util.Try

import zio.{Chunk, NonEmptyChunk}

/**
 * Represents an AcceptEncoding header value.
 */
sealed trait AcceptEncoding {
  val raw: String
}

object AcceptEncoding {

  /**
   * A compression format that uses the Brotli algorithm.
   */
  final case class Br(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "br"
  }

  /**
   * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
   */
  final case class Compress(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "compress"
  }

  /**
   * A compression format that uses the zlib structure with the deflate
   * compression algorithm.
   */
  final case class Deflate(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "deflate"
  }

  /**
   * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
   * CRC.
   */
  final case class GZip(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "gzip"
  }

  /**
   * Indicates the identity function (that is, without modification or
   * compression). This value is always considered as acceptable, even if
   * omitted.
   */
  final case class Identity(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "identity"
  }

  /**
   * Maintains a chunk of AcceptEncoding values.
   */
  final case class Multiple(encodings: NonEmptyChunk[AcceptEncoding]) extends AcceptEncoding {
    override val raw: String = encodings.map(_.raw).mkString(",")
  }

  /**
   * Matches any content encoding not already listed in the header. This is the
   * default value if the header is not present.
   */
  final case class NoPreference(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "*"
  }

  private def identifyEncodingFull(raw: String): Option[AcceptEncoding] = {
    val index = raw.indexOf(";q=")
    if (index == -1)
      identifyEncoding(raw)
    else {
      identifyEncoding(raw.substring(0, index), weight = Try(raw.substring(index + 3).toDouble).toOption)
    }
  }

  private def identifyEncoding(raw: String, weight: Option[Double] = None): Option[AcceptEncoding] = {
    raw.trim match {
      case "br"       => Some(Br(weight))
      case "compress" => Some(Compress(weight))
      case "deflate"  => Some(Deflate(weight))
      case "gzip"     => Some(GZip(weight))
      case "identity" => Some(Identity(weight))
      case "*"        => Some(NoPreference(weight))
      case _          => None
    }
  }

  def parse(value: String): Either[String, AcceptEncoding] = {
    val index = value.indexOf(",")

    @tailrec def loop(value: String, index: Int, acc: Chunk[AcceptEncoding]): Either[String, Chunk[AcceptEncoding]] = {
      if (index == -1) {
        identifyEncodingFull(value) match {
          case Some(encoding) =>
            Right(acc :+ encoding)
          case None           =>
            Left(s"Invalid accept encoding ($value)")
        }
      } else {
        val valueChunk = value.substring(0, index)
        val remaining  = value.substring(index + 1)
        val nextIndex  = remaining.indexOf(",")

        identifyEncodingFull(valueChunk) match {
          case Some(encoding) =>
            loop(
              remaining,
              nextIndex,
              acc :+ encoding,
            )
          case None           =>
            Left(s"Invalid accept encoding ($valueChunk)")
        }
      }
    }

    if (index == -1)
      identifyEncodingFull(value) match {
        case Some(encoding) => Right(encoding)
        case None           => Left(s"Invalid accept encoding ($value)")
      }
    else
      loop(value, index, Chunk.empty[AcceptEncoding]).flatMap { encodings =>
        NonEmptyChunk.fromChunk(encodings) match {
          case Some(value) => Right(Multiple(value))
          case None        => Left(s"Invalid accept encoding ($value)")
        }
      }
  }

  def render(encoding: AcceptEncoding): String =
    encoding match {
      case b @ Br(weight)           => weight.fold(b.raw)(value => s"${b.raw};q=$value")
      case c @ Compress(weight)     => weight.fold(c.raw)(value => s"${c.raw};q=$value")
      case d @ Deflate(weight)      => weight.fold(d.raw)(value => s"${d.raw};q=$value")
      case g @ GZip(weight)         => weight.fold(g.raw)(value => s"${g.raw};q=$value")
      case i @ Identity(weight)     => weight.fold(i.raw)(value => s"${i.raw};q=$value")
      case Multiple(encodings)      => encodings.map(render).mkString(",")
      case n @ NoPreference(weight) => weight.fold(n.raw)(value => s"${n.raw};q=$value")
    }

}
