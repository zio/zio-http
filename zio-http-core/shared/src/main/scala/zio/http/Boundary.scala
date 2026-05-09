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

package zio.http

import java.nio.charset.Charset

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace}

/**
 * A multipart boundary, which consists of both the boundary and its charset.
 */
final case class Boundary(value: String, charset: Charset) { self =>
  val closingBoundary: String = s"--$value--"

  val encapsulationBoundary: String = s"--$value"

  def isClosing(bytes: Chunk[Byte]): Boolean = bytes == closingBoundaryBytes

  def isEncapsulating(bytes: Chunk[Byte]): Boolean = bytes == encapsulationBoundaryBytes

  private[http] val encapsulationBoundaryBytes = Chunk.fromArray(encapsulationBoundary.getBytes(charset))

  private[http] val closingBoundaryBytes = Chunk.fromArray(closingBoundary.getBytes(charset))

  override def toString: String = value
}

object Boundary {
  def apply(boundary: String): Boundary = Boundary(boundary, Charsets.Utf8)

  def fromContent(content: Chunk[Byte], charset: Charset = Charsets.Utf8): Option[Boundary] = {
    var i = 0
    var j = 0

    val boundaryBytes = content.foldWhile(Chunk.empty[Byte])(_ => i < 2) {
      case (buffer, byte) if byte == '\r' || byte == '\n' =>
        i = i + 1
        buffer
      case (buffer, byte) if byte == '-' && j < 2         =>
        j = j + 1
        buffer
      case (buffer, byte)                                 =>
        buffer :+ byte
    }
    if (i == 2 && j == 2)
      Some(Boundary(new String(boundaryBytes.toArray, charset)))
    else Option.empty
  }

  def generate: Boundary = {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val sb    = new StringBuilder(24)
    var i     = 0
    while (i < 24) {
      sb.append(chars.charAt(scala.util.Random.nextInt(chars.length)))
      i += 1
    }
    Boundary(sb.toString)
  }

  def randomUUID(implicit trace: Trace): zio.UIO[Boundary] =
    zio.Random.nextUUID.map { id =>
      Boundary(s"----${id.toString}----")
    }
}
