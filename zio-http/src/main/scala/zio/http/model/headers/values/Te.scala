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

sealed trait Te {
  def raw: String
}

object Te {

  /**
   * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
   */
  final case class Compress(weight: Option[Double]) extends Te {
    override def raw: String = "compress"
  }

  /**
   * A compression format that uses the zlib structure with the deflate
   * compression algorithm.
   */
  final case class Deflate(weight: Option[Double]) extends Te {
    override def raw: String = "deflate"
  }

  /**
   * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
   * CRC.
   */
  final case class GZip(weight: Option[Double]) extends Te {
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
  final case class Multiple(encodings: NonEmptyChunk[Te]) extends Te {
    override def raw: String = encodings.mkString(",")
  }

  private def identifyTeFull(raw: String): Option[Te] = {
    val index = raw.indexOf(";q=")
    if (index == -1)
      identifyTe(raw)
    else {
      identifyTe(raw.substring(0, index), Try(raw.substring(index + 3).toDouble).toOption)
    }
  }

  private def identifyTe(raw: String, weight: Option[Double] = None): Option[Te] = {
    raw.trim match {
      case "compress" => Some(Compress(weight))
      case "deflate"  => Some(Deflate(weight))
      case "gzip"     => Some(GZip(weight))
      case "trailers" => Some(Trailers)
      case _          => None
    }
  }

  def parse(value: String): Either[String, Te] = {
    val index = value.indexOf(",")

    @tailrec def loop(value: String, index: Int, acc: Chunk[Te]): Either[String, Chunk[Te]] = {
      if (index == -1) {
        identifyTeFull(value) match {
          case Some(te) => Right(acc :+ te)
          case None     => Left("Invalid TE header")
        }
      } else {
        val valueChunk = value.substring(0, index)
        val remaining  = value.substring(index + 1)
        val nextIndex  = remaining.indexOf(",")
        identifyTeFull(valueChunk) match {
          case Some(te) => loop(remaining, nextIndex, acc :+ te)
          case None     => Left("Invalid TE header")
        }
      }
    }

    if (index == -1)
      identifyTeFull(value).toRight("Invalid TE header")
    else
      loop(value, index, Chunk.empty[Te]).flatMap { encodings =>
        NonEmptyChunk.fromChunk(encodings).toRight("Invalid TE header").map(Multiple(_))
      }
  }

  def render(encoding: Te): String = encoding match {
    case c @ Compress(weight) => weight.fold(c.raw)(value => s"${c.raw};q=$value")
    case d @ Deflate(weight)  => weight.fold(d.raw)(value => s"${d.raw};q=$value")
    case g @ GZip(weight)     => weight.fold(g.raw)(value => s"${g.raw};q=$value")
    case Multiple(encodings)  => encodings.map(render).mkString(", ")
    case Trailers             => Trailers.raw
  }

}
