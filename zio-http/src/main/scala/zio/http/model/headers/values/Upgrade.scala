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

sealed trait Upgrade

object Upgrade {
  final case class Multiple(protocols: Chunk[Protocol])        extends Upgrade
  final case class Protocol(protocol: String, version: String) extends Upgrade

  def fromUpgrade(upgrade: Upgrade): String =
    upgrade match {
      case Multiple(protocols)         => protocols.map(fromUpgrade).mkString(", ")
      case Protocol(protocol, version) => s"$protocol/$version"
    }

  private def parseProtocol(value: String): Either[String, Protocol] =
    Chunk.fromArray(value.split("/")).map(_.trim) match {
      case Chunk(protocol, version) => Right(Protocol(protocol, version))
      case _                        => Left("Invalid Upgrade header")
    }

  def toUpgrade(value: String): Either[String, Upgrade] = {
    Chunk.fromArray(value.split(",")).map(parseProtocol) match {
      case Chunk()       => Left("Invalid Upgrade header")
      case Chunk(single) => single
      case multiple      =>
        multiple
          .foldLeft[Either[String, Chunk[Protocol]]](Right(Chunk.empty)) {
            case (Right(protocols), Right(protocol)) => Right(protocols :+ protocol)
            case (Left(error), _)                    => Left(error)
            case (_, Left(error))                    => Left(error)
          }
          .map(Multiple(_))
    }
  }
}
