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

import zio.{Chunk, NonEmptyChunk}

sealed trait Upgrade

object Upgrade {
  final case class Multiple(protocols: NonEmptyChunk[Protocol]) extends Upgrade
  final case class Protocol(protocol: String, version: String)  extends Upgrade

  def parse(value: String): Either[String, Upgrade] = {
    NonEmptyChunk.fromChunk(Chunk.fromArray(value.split(",")).map(parseProtocol)) match {
      case None        => Left("Invalid Upgrade header")
      case Some(value) =>
        if (value.size == 1) value.head
        else
          value.tail
            .foldLeft(value.head.map(NonEmptyChunk.single(_))) {
              case (Right(acc), Right(value)) => Right(acc :+ value)
              case (Left(error), _)           => Left(error)
              case (_, Left(value))           => Left(value)
            }
            .map(Multiple(_))
    }
  }

  def render(upgrade: Upgrade): String =
    upgrade match {
      case Multiple(protocols)         => protocols.map(render).mkString(", ")
      case Protocol(protocol, version) => s"$protocol/$version"
    }

  private def parseProtocol(value: String): Either[String, Protocol] =
    Chunk.fromArray(value.split("/")).map(_.trim) match {
      case Chunk(protocol, version) => Right(Protocol(protocol, version))
      case _                        => Left("Invalid Upgrade header")
    }
}
