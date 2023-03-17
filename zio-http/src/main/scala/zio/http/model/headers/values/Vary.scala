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

/** Vary header value. */
sealed trait Vary

object Vary {
  case class Headers(headers: Chunk[String]) extends Vary
  case object Star                           extends Vary

  def parse(value: String): Either[String, Vary] = {
    Chunk.fromArray(value.toLowerCase().split("[, ]+")) match {
      case Chunk("*")                                => Right(Star)
      case chunk if chunk.nonEmpty && value.nonEmpty => Right(Headers(chunk.map(_.trim)))
      case _                                         => Left("Invalid Vary header")
    }
  }

  def render(vary: Vary): String = {
    vary match {
      case Star          => "*"
      case Headers(list) => list.mkString(", ")
    }
  }
}
