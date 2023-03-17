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

import zio.http.model.Method

sealed trait AccessControlAllowMethods

object AccessControlAllowMethods {

  final case class Some(methods: NonEmptyChunk[Method]) extends AccessControlAllowMethods

  case object All extends AccessControlAllowMethods

  case object None extends AccessControlAllowMethods

  def parse(value: String): Either[String, AccessControlAllowMethods] = {
    Right {
      value match {
        case ""          => None
        case "*"         => All
        case methodNames =>
          NonEmptyChunk.fromChunk(
            Chunk.fromArray(
              methodNames
                .split(",")
                .map(_.trim)
                .map(Method.fromString),
            ),
          ) match {
            case scala.Some(value) => Some(value)
            case scala.None        => None
          }
      }
    }
  }

  def render(accessControlAllowMethods: AccessControlAllowMethods): String =
    accessControlAllowMethods match {
      case Some(methods) => methods.map(_.toString()).mkString(", ")
      case All           => "*"
      case None          => ""
    }
}
