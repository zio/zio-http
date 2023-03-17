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

sealed trait Via

/**
 * The Via general header is added by proxies, both forward and reverse, and can
 * appear in the request or response headers. It is used for tracking message
 * forwards, avoiding request loops, and identifying the protocol capabilities
 * of senders along the request/response chain
 */
object Via {
  sealed trait ReceivedProtocol
  object ReceivedProtocol {
    final case class Version(version: String)                           extends ReceivedProtocol
    final case class ProtocolVersion(protocol: String, version: String) extends ReceivedProtocol
  }

  final case class Detailed(receivedProtocol: ReceivedProtocol, receivedBy: String, comment: Option[String]) extends Via
  final case class Multiple(values: Chunk[Via])                                                              extends Via

  def toVia(values: String): Either[String, Via] = {
    val viaValues = Chunk.fromArray(values.split(',')).map(_.trim).map { value =>
      Chunk.fromArray(value.split(' ')) match {
        case Chunk(receivedProtocol, receivedBy)          =>
          toReceivedProtocol(receivedProtocol).map { rp =>
            Detailed(rp, receivedBy, None)
          }
        case Chunk(receivedProtocol, receivedBy, comment) =>
          toReceivedProtocol(receivedProtocol).map { rp =>
            Detailed(rp, receivedBy, Some(comment))
          }
        case _                                            =>
          Left("Invalid Via header")
      }
    }

    viaValues match {
      case Chunk()       => Left("Invalid Via header")
      case Chunk(single) => single
      case multiple      =>
        multiple
          .foldLeft[Either[String, Chunk[Via]]](Right(Chunk.empty)) {
            case (Right(vias), Right(via)) => Right(vias :+ via)
            case (Left(error), _)          => Left(error)
            case (_, Left(error))          => Left(error)
          }
          .map(Multiple(_))
    }
  }

  def fromVia(via: Via): String =
    via match {
      case Multiple(values)                                =>
        values.map(fromVia).mkString(", ")
      case Detailed(receivedProtocol, receivedBy, comment) =>
        s"${fromReceivedProtocol(receivedProtocol)} $receivedBy ${comment.getOrElse("")}"
    }

  private def fromReceivedProtocol(receivedProtocol: ReceivedProtocol): String =
    receivedProtocol match {
      case ReceivedProtocol.Version(version)                   => version
      case ReceivedProtocol.ProtocolVersion(protocol, version) => s"$protocol/$version"
    }

  private def toReceivedProtocol(value: String): Either[String, ReceivedProtocol] = {
    value.split('/').toList match {
      case version :: Nil             => Right(ReceivedProtocol.Version(version))
      case protocol :: version :: Nil => Right(ReceivedProtocol.ProtocolVersion(protocol, version))
      case _                          => Left("Invalid received protocol")
    }
  }

}
